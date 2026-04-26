package com.cloudalbum.publisher.media.content;

import com.cloudalbum.publisher.common.enums.ResultCode;
import com.cloudalbum.publisher.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Locale;

@Slf4j
@Component
public class MediaHttpWriter {

    public void write(MediaContentResource resource,
                      HttpServletRequest request,
                      HttpServletResponse response,
                      String failureMessage) {
        try {
            long fileSize = resource.getContentLength();
            String contentType = resource.getContentType();

            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Cache-Control", "no-store");
            if (StringUtils.hasText(contentType)) {
                response.setContentType(contentType);
            }

            long[] range = resolveRange(request.getHeader("Range"), fileSize);
            if (range != null && range[0] < 0) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }

            if (range == null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLengthLong(fileSize);
                try (InputStream inputStream = resource.open()) {
                    StreamUtils.copy(inputStream, response.getOutputStream());
                }
                return;
            }

            long start = range[0];
            long end = range[1];
            long contentLength = end - start + 1;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            response.setContentLengthLong(contentLength);
            try (InputStream inputStream = resource.open(start, contentLength)) {
                StreamUtils.copy(inputStream, response.getOutputStream());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isClientAbort(ex)) {
                log.debug("Client aborted media http response", ex);
                return;
            }
            log.error("Write media http response failed", ex);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, failureMessage);
        }
    }

    private boolean isClientAbort(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            String lowerMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if (className.endsWith("ClientAbortException")
                    || className.endsWith("AsyncRequestNotUsableException")
                    || lowerMessage.contains("broken pipe")
                    || lowerMessage.contains("connection reset")
                    || lowerMessage.contains("connection aborted")
                    || lowerMessage.contains("software caused connection abort")
                    || lowerMessage.contains("你的主机中的软件中止了一个已建立的连接")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long[] resolveRange(String rangeHeader, long fileSize) {
        if (!StringUtils.hasText(rangeHeader) || !rangeHeader.startsWith("bytes=")) {
            return null;
        }
        String rangeValue = rangeHeader.substring(6).trim();
        int commaIndex = rangeValue.indexOf(',');
        if (commaIndex >= 0) {
            rangeValue = rangeValue.substring(0, commaIndex).trim();
        }
        String[] parts = rangeValue.split("-", 2);
        if (parts.length != 2) {
            return new long[]{-1, -1};
        }
        try {
            long start;
            long end;
            if (!StringUtils.hasText(parts[0])) {
                long suffixLength = Long.parseLong(parts[1]);
                if (suffixLength <= 0 || fileSize <= 0) {
                    return new long[]{-1, -1};
                }
                start = Math.max(fileSize - suffixLength, 0);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(parts[0]);
                end = StringUtils.hasText(parts[1]) ? Long.parseLong(parts[1]) : fileSize - 1;
            }
            if (start < 0 || end < start || start >= fileSize) {
                return new long[]{-1, -1};
            }
            return new long[]{start, Math.min(end, fileSize - 1)};
        } catch (NumberFormatException ex) {
            return new long[]{-1, -1};
        }
    }
}
