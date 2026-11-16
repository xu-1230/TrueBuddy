package org.example.backend.module.knowledgebase.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.knowledgebase.model.vo.PublicSpaceRespVO;
import org.example.backend.module.knowledgebase.model.vo.SpaceRespVO;
import org.example.backend.module.knowledgebase.model.vo.SpaceSaveReqVO;
import org.example.backend.module.knowledgebase.service.KbSpaceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 知识库空间接口
 */
@RestController
@RequestMapping("/knowledge-base/space")
public class KbSpaceController {

    @Resource
    private KbSpaceService spaceService;

    @GetMapping("/list")
    public CommonResult<List<SpaceRespVO>> list() {
        return success(spaceService.getSpaceList());
    }

    /** 他人的公有空间列表（「公有库」浏览区，只读） */
    @GetMapping("/public-list")
    public CommonResult<List<PublicSpaceRespVO>> publicList() {
        return success(spaceService.getPublicSpaceList());
    }

    /** 切换空间可见性（仅创建者可调）：visibility 0=私有 / 1=公有 */
    @PutMapping("/visibility")
    public CommonResult<Boolean> updateVisibility(@RequestParam("id") Long id,
                                                  @RequestParam("visibility") Integer visibility) {
        spaceService.updateSpaceVisibility(id, visibility);
        return success(true);
    }

    /** fork 空间为「添加为自己的」独立副本（单事务复制文档/分片/向量），返回副本空间 id */
    @PostMapping("/fork")
    public CommonResult<Long> fork(@RequestParam("id") Long id) {
        return success(spaceService.forkSpace(id));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody SpaceSaveReqVO reqVO) {
        return success(spaceService.createSpace(reqVO));
    }

    @PutMapping("/update")
    public CommonResult<SpaceRespVO> update(@RequestBody SpaceSaveReqVO reqVO) {
        return success(spaceService.updateSpace(reqVO));
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        spaceService.deleteSpace(id);
        return success(true);
    }

}
