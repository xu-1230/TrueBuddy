package org.example.backend.module.knowledgebase.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
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
