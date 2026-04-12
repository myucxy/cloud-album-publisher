package com.cloudalbum.publisher.album.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudalbum.publisher.album.entity.Album;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlbumMapper extends BaseMapper<Album> {
}
