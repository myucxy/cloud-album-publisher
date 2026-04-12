package com.cloudalbum.publisher.audit.annotation;

import java.lang.annotation.*;

/**
 * 标注在 Controller 方法上，触发操作审计日志记录。
 *
 * <pre>
 * &#64;Audit(action = "ALBUM_DELETE", resourceType = "album")
 * public Result<Void> deleteAlbum(@PathVariable Long id) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audit {

    /** 操作类型，建议使用大写下划线格式，如：ALBUM_DELETE */
    String action();

    /** 资源类型，小写单词，如：album / media / distribution */
    String resourceType() default "";
}
