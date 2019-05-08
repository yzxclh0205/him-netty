package com.lmxdawn.him.api.controller.group;

import com.lmxdawn.him.api.dto.UserLoginDTO;
import com.lmxdawn.him.api.enums.WSReqTypeEnum;
import com.lmxdawn.him.api.service.group.GroupMsgService;
import com.lmxdawn.him.api.service.group.GroupService;
import com.lmxdawn.him.api.service.group.GroupUserService;
import com.lmxdawn.him.api.service.user.UserService;
import com.lmxdawn.him.api.service.ws.WSService;
import com.lmxdawn.him.api.utils.UserLoginUtils;
import com.lmxdawn.him.api.vo.req.GroupMsgCreateReqVO;
import com.lmxdawn.him.api.vo.req.WSMessageReqVO;
import com.lmxdawn.him.api.vo.res.GroupMsgListResVO;
import com.lmxdawn.him.api.vo.res.UserInfoListResVO;
import com.lmxdawn.him.common.entity.group.GroupMsg;
import com.lmxdawn.him.common.entity.group.GroupUser;
import com.lmxdawn.him.common.enums.ResultEnum;
import com.lmxdawn.him.common.utils.ResultVOUtils;
import com.lmxdawn.him.common.vo.res.BaseResVO;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 群消息相关
 */
@RequestMapping("/group/msg")
@RestController
public class GroupMsgController {

    @Resource
    private GroupUserService groupUserService;

    @Resource
    private GroupMsgService groupMsgService;

    @Resource
    private UserService userService;
    
    @Resource
    private WSService wsService;

    /**
     * 列表
     *
     * @return
     */
    @GetMapping("/lists")
    public BaseResVO lists(@RequestParam("groupId") Long groupId,
                           @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                           @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                           HttpServletRequest request) {

        limit = limit > 25 ? 25 : limit;
        // 验证登录
        UserLoginDTO userLoginDTO = UserLoginUtils.check(request);
        if (userLoginDTO == null) {
            return ResultVOUtils.error(ResultEnum.LOGIN_VERIFY_FALL);
        }

        Long uid = userLoginDTO.getUid();

        // 判断是不是群里的人
        GroupUser groupUser = groupUserService.findByGroupIdAndUid(groupId, uid);
        if (groupUser == null) {
            return ResultVOUtils.error(ResultEnum.PARAM_VERIFY_FALL, "不是该群成员~");
        }

        List<GroupMsg> groupMsgs = groupMsgService.listByGroupIdAndCreateTime(groupId, groupUser.getCreateTime(), page, limit);

        List<GroupMsgListResVO> groupMsgListResVOS = new ArrayList<>();
        if (groupMsgs.size() == 0) {
            return ResultVOUtils.success(groupMsgListResVOS);
        }

        List<Long> uids = groupMsgs.stream().map(GroupMsg::getSenderUid).collect(Collectors.toList());
        Map<Long, UserInfoListResVO> userInfoListResVOMap = userService.listByUidIn(uids);

        groupMsgs.forEach(v -> {
            GroupMsgListResVO groupMsgListResVO = new GroupMsgListResVO();
            BeanUtils.copyProperties(v, groupMsgListResVO);
            groupMsgListResVO.setUser(userInfoListResVOMap.get(v.getSenderUid()));
            groupMsgListResVOS.add(groupMsgListResVO);
        });

        return ResultVOUtils.success(groupMsgListResVOS);
    }

    /**
     * 添加
     *
     * @return
     */
    @PostMapping("/create")
    public BaseResVO create(@Valid @RequestBody GroupMsgCreateReqVO groupMsgCreateReqVO,
                            BindingResult bindingResult,
                            HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return ResultVOUtils.error(ResultEnum.PARAM_VERIFY_FALL, bindingResult.getFieldError().getDefaultMessage());
        }

        // 验证登录
        UserLoginDTO userLoginDTO = UserLoginUtils.check(request);
        if (userLoginDTO == null) {
            return ResultVOUtils.error(ResultEnum.LOGIN_VERIFY_FALL);
        }

        Long uid = userLoginDTO.getUid();

        Long groupId = groupMsgCreateReqVO.getGroupId();
        // 判断是否在群里面
        GroupUser groupUser1 = groupUserService.findByGroupIdAndUid(groupId, uid);
        if (groupUser1 == null) {
            return ResultVOUtils.error(ResultEnum.PARAM_VERIFY_FALL, "不是该群成员~");
        }

        Integer msgType = groupMsgCreateReqVO.getMsgType();
        String msgContent = groupMsgCreateReqVO.getMsgContent();
        boolean b = groupMsgService.addpMsg(uid, groupId, msgType, msgContent);
        if (!b) {
            return ResultVOUtils.error(ResultEnum.NOT_NETWORK);
        }
    
    
        // 发送入群消息
        WSMessageReqVO wsMessageReqVO = new WSMessageReqVO();
        wsMessageReqVO.setType(WSReqTypeEnum.FRIEND_ACK.getType());
        wsMessageReqVO.setSenderId(uid);
        wsMessageReqVO.setReceiveId(groupId);
        wsMessageReqVO.setMsgType(0);
        wsMessageReqVO.setMsgContent(msgContent);
    
        // 查找群里的所有用户信息
        List<GroupUser> groupUsers = groupUserService.listByGroupId(groupId, 1, 500);
    
        groupUsers.forEach(v -> {
            System.out.println(v.getUid());
            // 排除自己
            if (!uid.equals(v.getUid())) {
                wsService.sendMsg(v.getUid(), wsMessageReqVO);
            }
        });

        return ResultVOUtils.success();
    }

}