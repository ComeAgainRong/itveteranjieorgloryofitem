package com.ams.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.ams.admin.config.AdminMapStruct;
import com.ams.admin.dto.UserAuthDTO;
import com.ams.admin.mapper.SysUserMapper;
import com.ams.admin.pojo.entity.*;
import com.ams.admin.pojo.req.SaveUserReq;
import com.ams.admin.pojo.req.UserListPageReq;
import com.ams.admin.pojo.vo.SysUserVO;
import com.ams.admin.service.*;
import com.ams.admin.utils.PageUtils;
import com.ams.common.constan.GlobalConstants;
import com.ams.common.entity.APage;
import com.ams.common.result.ResultCode;
import com.ams.common.utils.AssertUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 *
 * @author： whisper
 * @date： 2021/11/24
 * @description：
 * @modifiedBy：
 * @version: 1.0
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {
    private final PasswordEncoder passwordEncoder;
    private  final ISysUserRoleService userRoleService;
    private final AdminMapStruct adminMapStruct;
    @Override
    public UserAuthDTO getByUsername(String username) {
        UserAuthDTO userAuthInfo = this.baseMapper.getByUsername(username);
        return userAuthInfo;
    }

    @Override
    public void createUser(SaveUserReq req) {
        // 生成密码
        String passwd = passwordEncoder.encode(GlobalConstants.USER_DEFAULT_PASSWORD);
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(req, sysUser);
        sysUser.setPassword(passwd);
        save(sysUser);
        // 维护角色关系
        saveUserRoles(req.getRoleIds(), sysUser.getId());

    }

    @Override
    public SysUserVO userDetail(Long userId) {
        SysUser sysUser= lambdaQuery().eq(SysUser::getId,userId).one();
        AssertUtil.notEmpty(sysUser, ResultCode.USER_NOT_EXIST);
        SysUserVO sysUserVO=new SysUserVO();
        BeanUtil.copyProperties(sysUser,sysUserVO);
        //查询绑定的角色IDs
        List<Long> roleIds= userRoleService.selectRoleIds(userId);
        sysUserVO.setRoleIds(roleIds);
        return sysUserVO;
    }


    /**
     * 更新用户信息
     * @param userReq
     * @param userId
     */
    @Override
    public void updateUserInfo(SaveUserReq userReq, Long userId) {
        SysUser sysUser=new SysUser();
        BeanUtils.copyProperties(userReq,sysUser);
        lambdaUpdate().eq(SysUser::getId,userId).update(sysUser);
    }

    @Override
    public void mulDeleteUsers(List<Long> userIds) {
        //删除用户信息 逻辑删除
        lambdaUpdate().in(SysUser::getId,userIds).set(SysUser::getDeleted,GlobalConstants.STATUS_ON).update();
        //删除用户关联的角色
        userRoleService.getBaseMapper().delete(userRoleService.lambdaQuery().in(SysUserRole::getUserId, userIds).getWrapper());

    }

    @Override
    public APage<SysUserVO> listPage(UserListPageReq req) {
        Page<SysUser> page = new Page<>();
        page.setCurrent(req.getPageNo());
        page.setSize(req.getPageSize());
        LambdaQueryChainWrapper<SysUser> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(SysUser::getStatus, GlobalConstants.STATUS_ON);
        if (StringUtils.isNoneBlank(req.getKeyword())) {
            lambdaQuery.like(SysUser::getUsername, req.getKeyword()).or().like(SysUser::getNickname, req.getKeyword());
        }
        baseMapper.selectPage(page, lambdaQuery.getWrapper());
        List<SysUser> records = page.getRecords();
        List<SysUserVO> sysUserVOS = adminMapStruct.sysUserToSysUserVO(records);
        return PageUtils.flush(page, sysUserVOS);
    }


    private void saveUserRoles(List<Long> roleIds,Long userId) {
        if (CollectionUtil.isNotEmpty(roleIds)) {
            List<SysUserRole> sysUserRoles = new ArrayList<>();
            roleIds.forEach(roleId -> {
                sysUserRoles.add(new SysUserRole(userId, roleId));
            });
            userRoleService.saveBatch(sysUserRoles);
        }
    }

}
