package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.RoomType;
import org.example.springboot.entity.RoomTypeImage;
import org.example.springboot.entity.Room;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.RoomTypeMapper;
import org.example.springboot.mapper.RoomTypeImageMapper;
import org.example.springboot.mapper.RoomMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 房间类型服务类
 */
@Service
public class RoomTypeService {
    @Resource
    private RoomTypeMapper roomTypeMapper;
    
    @Resource
    private RoomMapper roomMapper;
    
    @Resource
    private RoomTypeImageMapper roomTypeImageMapper;
    
    /**
     * 分页查询房间类型
     */
    public Page<RoomType> getRoomTypesByPage(String name, Integer minPrice, Integer maxPrice, 
                                            Integer maxPeople, Integer currentPage, Integer size) {
        LambdaQueryWrapper<RoomType> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        if (StringUtils.isNotBlank(name)) {
            queryWrapper.like(RoomType::getName, name);
        }
        if (minPrice != null) {
            queryWrapper.ge(RoomType::getPrice, minPrice);
        }
        if (maxPrice != null) {
            queryWrapper.le(RoomType::getPrice, maxPrice);
        }
        if (maxPeople != null) {
            queryWrapper.eq(RoomType::getMaxPeople, maxPeople);
        }
        
        // 排序
        queryWrapper.orderByAsc(RoomType::getPrice);
        
        // 分页查询
        Page<RoomType> page = roomTypeMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
        
        // 查询每种房型的房间数量和图片
        for (RoomType roomType : page.getRecords()) {
            // 查询总房间数
            int roomCount = Math.toIntExact(roomMapper.selectCount(
                    new LambdaQueryWrapper<Room>()
                            .eq(Room::getRoomTypeId, roomType.getId())
            ));
            
            // 查询可用房间数
            int availableRoomCount = Math.toIntExact(roomMapper.selectCount(
                    new LambdaQueryWrapper<Room>()
                            .eq(Room::getRoomTypeId, roomType.getId())
                            .eq(Room::getStatus, 1)
            ));
            
            roomType.setRoomCount(roomCount);
            roomType.setAvailableRoomCount(availableRoomCount);
            
            // 查询房型图片
            loadRoomTypeImages(roomType);
        }
        
        return page;
    }
    
    /**
     * 获取所有房间类型
     */
    public List<RoomType> getAllRoomTypes() {
        List<RoomType> roomTypes = roomTypeMapper.selectList(null);
        
        // 查询每种房型的房间数量和图片
        for (RoomType roomType : roomTypes) {
            // 查询总房间数
            Long roomCount = roomMapper.selectCount(
                new LambdaQueryWrapper<Room>()
                    .eq(Room::getRoomTypeId, roomType.getId())
            );
            
            // 查询可用房间数
            Long availableRoomCount = roomMapper.selectCount(
                new LambdaQueryWrapper<Room>()
                    .eq(Room::getRoomTypeId, roomType.getId())
                    .eq(Room::getStatus, 1)
            );
            
            roomType.setRoomCount(Math.toIntExact(roomCount));
            roomType.setAvailableRoomCount(Math.toIntExact(availableRoomCount));
            
            // 查询房型图片
            loadRoomTypeImages(roomType);
        }
        
        return roomTypes;
    }
    
    /**
     * 根据ID获取房间类型
     */
    public RoomType getRoomTypeById(Long id) {
        RoomType roomType = roomTypeMapper.selectById(id);
        if (roomType == null) {
            throw new ServiceException("房间类型不存在");
        }
        
        // 查询总房间数
        int roomCount = Math.toIntExact(roomMapper.selectCount(
                new LambdaQueryWrapper<Room>()
                        .eq(Room::getRoomTypeId, roomType.getId())
        ));
        
        // 查询可用房间数
        int availableRoomCount = Math.toIntExact(roomMapper.selectCount(
                new LambdaQueryWrapper<Room>()
                        .eq(Room::getRoomTypeId, roomType.getId())
                        .eq(Room::getStatus, 1)
        ));
        
        roomType.setRoomCount(roomCount);
        roomType.setAvailableRoomCount(availableRoomCount);
        
        // 查询房型图片
        loadRoomTypeImages(roomType);
        
        return roomType;
    }
    
    /**
     * 查询房型图片
     */
    private void loadRoomTypeImages(RoomType roomType) {
        if (roomType == null) return;
        
        LambdaQueryWrapper<RoomTypeImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomTypeImage::getRoomTypeId, roomType.getId());
        queryWrapper.orderByAsc(RoomTypeImage::getSortOrder);
        
        List<RoomTypeImage> images = roomTypeImageMapper.selectList(queryWrapper);
        roomType.setImages(images);
    }
    
    /**
     * 创建房间类型
     */
    @Transactional
    public void createRoomType(RoomType roomType) {
        // 检查房型名称是否已存在
        if (roomTypeMapper.selectOne(
                new LambdaQueryWrapper<RoomType>()
                    .eq(RoomType::getName, roomType.getName())
            ) != null) {
            throw new ServiceException("房型名称已存在");
        }
        
        if (roomTypeMapper.insert(roomType) <= 0) {
            throw new ServiceException("创建房间类型失败");
        }
        
        // 保存图片
        saveRoomTypeImages(roomType.getId(), roomType.getImages());
    }
    
    /**
     * 更新房间类型
     */
    @Transactional
    public void updateRoomType(Long id, RoomType roomType) {
        // 检查房间类型是否存在
        RoomType existingRoomType = roomTypeMapper.selectById(id);
        if (existingRoomType == null) {
            throw new ServiceException("要更新的房间类型不存在");
        }
        
        // 检查房型名称是否与其他房型重复
        if (roomType.getName() != null && !roomType.getName().equals(existingRoomType.getName())) {
            RoomType duplicateRoomType = roomTypeMapper.selectOne(
                new LambdaQueryWrapper<RoomType>()
                    .eq(RoomType::getName, roomType.getName())
            );
            if (duplicateRoomType != null && !duplicateRoomType.getId().equals(id)) {
                throw new ServiceException("房型名称已被使用");
            }
        }
        
        roomType.setId(id);
        if (roomTypeMapper.updateById(roomType) <= 0) {
            throw new ServiceException("更新房间类型失败");
        }
        
        // 更新图片
        if (roomType.getImages() != null) {
            updateRoomTypeImages(id, roomType.getImages());
        }
    }
    
    /**
     * 删除房间类型
     */
    @Transactional
    public void deleteRoomType(Long id) {
        // 检查房间类型是否存在
        if (roomTypeMapper.selectById(id) == null) {
            throw new ServiceException("要删除的房间类型不存在");
        }
        
        // 检查是否有关联的房间
        int roomCount = Math.toIntExact(roomMapper.selectCount(
                new LambdaQueryWrapper<Room>()
                        .eq(Room::getRoomTypeId, id)
        ));
        if (roomCount > 0) {
            throw new ServiceException("该房间类型下有关联的房间，无法删除");
        }
        
        // 删除关联的图片
        roomTypeImageMapper.delete(
            new LambdaQueryWrapper<RoomTypeImage>()
                .eq(RoomTypeImage::getRoomTypeId, id)
        );
        
        if (roomTypeMapper.deleteById(id) <= 0) {
            throw new ServiceException("删除房间类型失败");
        }
    }
    
    /**
     * 保存房型图片
     */
    @Transactional
    public void saveRoomTypeImages(Long roomTypeId, List<RoomTypeImage> images) {
        if (roomTypeId == null || images == null || images.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < images.size(); i++) {
            RoomTypeImage image = images.get(i);
            image.setRoomTypeId(roomTypeId);
            image.setSortOrder(i);
            
            // 检查是否是主图
            if (i == 0) {
                image.setIsMain(1);
            } else {
                image.setIsMain(0);
            }
            
            roomTypeImageMapper.insert(image);
        }
        
        // 更新主图片URL到房型表
        if (!images.isEmpty()) {
            RoomTypeImage mainImage = images.get(0);
            RoomType roomType = new RoomType();
            roomType.setId(roomTypeId);
            roomType.setImage(mainImage.getImageUrl());
            roomTypeMapper.updateById(roomType);
        }
    }
    
    /**
     * 更新房型图片
     */
    @Transactional
    public void updateRoomTypeImages(Long roomTypeId, List<RoomTypeImage> images) {
        if (roomTypeId == null) {
            return;
        }
        
        // 先删除原有图片
        roomTypeImageMapper.delete(
            new LambdaQueryWrapper<RoomTypeImage>()
                .eq(RoomTypeImage::getRoomTypeId, roomTypeId)
        );
        
        // 保存新图片
        saveRoomTypeImages(roomTypeId, images);
    }
    
    /**
     * 添加房型图片
     */
    @Transactional
    public void addRoomTypeImage(Long roomTypeId, RoomTypeImage image) {
        if (roomTypeId == null || image == null) {
            throw new ServiceException("参数错误");
        }
        
        // 检查房型是否存在
        RoomType roomType = roomTypeMapper.selectById(roomTypeId);
        if (roomType == null) {
            throw new ServiceException("房间类型不存在");
        }
        
        // 获取当前图片数量，设置排序
        int count = Math.toIntExact(roomTypeImageMapper.selectCount(
            new LambdaQueryWrapper<RoomTypeImage>()
                .eq(RoomTypeImage::getRoomTypeId, roomTypeId)
        ));
        
        image.setRoomTypeId(roomTypeId);
        image.setSortOrder(count);
        image.setIsMain(count == 0 ? 1 : 0);
        
        roomTypeImageMapper.insert(image);
        
        // 如果是第一张图片，则设为主图
        if (count == 0) {
            RoomType updateRoomType = new RoomType();
            updateRoomType.setId(roomTypeId);
            updateRoomType.setImage(image.getImageUrl());
            roomTypeMapper.updateById(updateRoomType);
        }
    }
    
    /**
     * 删除房型图片
     */
    @Transactional
    public void deleteRoomTypeImage(Long imageId) {
        RoomTypeImage image = roomTypeImageMapper.selectById(imageId);
        if (image == null) {
            throw new ServiceException("图片不存在");
        }
        
        // 检查是否是主图
        boolean isMainImage = image.getIsMain() != null && image.getIsMain() == 1;
        Long roomTypeId = image.getRoomTypeId();
        
        // 删除图片
        roomTypeImageMapper.deleteById(imageId);
        
        // 如果是主图，需要设置新的主图
        if (isMainImage) {
            // 查找下一张图片
            LambdaQueryWrapper<RoomTypeImage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(RoomTypeImage::getRoomTypeId, roomTypeId);
            queryWrapper.orderByAsc(RoomTypeImage::getSortOrder);
            queryWrapper.last("limit 1");
            
            RoomTypeImage newMainImage = roomTypeImageMapper.selectOne(queryWrapper);
            
            // 更新主图信息
            if (newMainImage != null) {
                // 设置为主图
                newMainImage.setIsMain(1);
                roomTypeImageMapper.updateById(newMainImage);
                
                // 更新房型表的图片URL
                RoomType roomType = new RoomType();
                roomType.setId(roomTypeId);
                roomType.setImage(newMainImage.getImageUrl());
                roomTypeMapper.updateById(roomType);
            } else {
                // 没有图片了，清空主图URL
                RoomType roomType = new RoomType();
                roomType.setId(roomTypeId);
                roomType.setImage(null);
                roomTypeMapper.updateById(roomType);
            }
        }
    }
    
    /**
     * 设置主图
     */
    @Transactional
    public void setMainImage(Long imageId) {
        RoomTypeImage image = roomTypeImageMapper.selectById(imageId);
        if (image == null) {
            throw new ServiceException("图片不存在");
        }
        
        Long roomTypeId = image.getRoomTypeId();
        
        // 将所有图片设为非主图
        LambdaQueryWrapper<RoomTypeImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RoomTypeImage::getRoomTypeId, roomTypeId);
        
        List<RoomTypeImage> images = roomTypeImageMapper.selectList(queryWrapper);
        for (RoomTypeImage img : images) {
            img.setIsMain(img.getId().equals(imageId) ? 1 : 0);
            roomTypeImageMapper.updateById(img);
        }
        
        // 更新房型表的图片URL
        RoomType roomType = new RoomType();
        roomType.setId(roomTypeId);
        roomType.setImage(image.getImageUrl());
        roomTypeMapper.updateById(roomType);
    }
} 