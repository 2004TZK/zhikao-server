package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.entity.Favorite;
import com.zhikao.server.mapper.FavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 收藏服务（契约 6.4/6.5/6.9，T5.x）。
 * toggle 幂等：已收藏取消，未收藏新增。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    /** 收藏/取消收藏（toggle），返回是否已收藏 */
    public boolean toggle(Long userId, Integer targetType, Long targetId) {
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, targetId)
                .last("LIMIT 1"));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return false;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(targetType);
        favorite.setTargetId(targetId);
        favoriteMapper.insert(favorite);
        return true;
    }

    /** 收藏列表 */
    public java.util.List<Favorite> list(Long userId, Integer targetType) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .orderByDesc(Favorite::getCreatedAt));
    }
}
