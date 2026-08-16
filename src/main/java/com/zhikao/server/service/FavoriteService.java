package com.zhikao.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhikao.server.entity.Favorite;
import com.zhikao.server.mapper.FavoriteMapper;
import com.zhikao.server.mapper.IdiomMapper;
import com.zhikao.server.mapper.KnowledgeMapper;
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
    private final KnowledgeMapper knowledgeMapper;
    private final IdiomMapper idiomMapper;

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

    /** 收藏列表（含标题，供收藏页展示） */
    public java.util.List<java.util.Map<String, Object>> listWithTitle(Long userId, Integer targetType) {
        java.util.List<Favorite> favorites = list(userId, targetType);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Favorite favorite : favorites) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", favorite.getId());
            item.put("targetType", favorite.getTargetType());
            item.put("targetId", favorite.getTargetId());
            item.put("createdAt", favorite.getCreatedAt());
            String title = null;
            if (targetType == 1) {
                com.zhikao.server.entity.Knowledge knowledge =
                        knowledgeMapper.selectById(favorite.getTargetId());
                if (knowledge != null) {
                    title = knowledge.getTitle();
                }
            } else {
                com.zhikao.server.entity.Idiom idiom = idiomMapper.selectById(favorite.getTargetId());
                if (idiom != null) {
                    title = idiom.getWord();
                }
            }
            item.put("title", title);
            result.add(item);
        }
        return result;
    }
}
