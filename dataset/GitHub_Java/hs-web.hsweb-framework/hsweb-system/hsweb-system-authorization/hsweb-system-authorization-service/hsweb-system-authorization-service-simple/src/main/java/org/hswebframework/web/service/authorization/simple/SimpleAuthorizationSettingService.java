/*
 *  Copyright 2016 http://www.hswebframework.org
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */
package org.hswebframework.web.service.authorization.simple;

import org.apache.commons.codec.digest.DigestUtils;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.AuthenticationInitializeService;
import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.access.DataAccessConfig;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimplePermission;
import org.hswebframework.web.authorization.simple.SimpleRole;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.hswebframework.web.commons.entity.DataStatus;
import org.hswebframework.web.commons.entity.TreeSupportEntity;
import org.hswebframework.web.dao.authorization.AuthorizationSettingDao;
import org.hswebframework.web.dao.authorization.AuthorizationSettingDetailDao;
import org.hswebframework.web.entity.authorization.*;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.service.DefaultDSLDeleteService;
import org.hswebframework.web.service.DefaultDSLQueryService;
import org.hswebframework.web.service.GenericEntityService;
import org.hswebframework.web.service.authorization.*;
import org.hswebframework.web.service.authorization.AuthorizationSettingTypeSupplier.SettingInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hswebframework.web.entity.authorization.AuthorizationSettingDetailEntity.STATE_OK;
import static org.hswebframework.web.entity.authorization.AuthorizationSettingEntity.settingFor;
import static org.hswebframework.web.entity.authorization.AuthorizationSettingEntity.type;
import static org.hswebframework.web.service.authorization.simple.CacheConstants.USER_AUTH_CACHE_NAME;

/**
 * 默认的服务实现
 *
 * @author hsweb-generator-online
 */
@Service("authorizationSettingService")
@CacheConfig(cacheNames = USER_AUTH_CACHE_NAME)
public class SimpleAuthorizationSettingService extends GenericEntityService<AuthorizationSettingEntity, String>
        implements AuthorizationSettingService, AuthenticationInitializeService, UserMenuManagerService {

    private AuthorizationSettingDao authorizationSettingDao;

    private AuthorizationSettingDetailDao authorizationSettingDetailDao;

    private AuthorizationSettingMenuService authorizationSettingMenuService;

    private MenuService menuService;

    private UserService userService;

    private List<AuthorizationSettingTypeSupplier> authorizationSettingTypeSuppliers;

    private DataAccessFactory dataAccessFactory;

    @Override
    protected IDGenerator<String> getIDGenerator() {
        return IDGenerator.MD5;
    }

    @Override
    public AuthorizationSettingDao getDao() {
        return authorizationSettingDao;
    }

    @Override
    public AuthorizationSettingEntity select(String type, String settingFor) {
        return createQuery().where(AuthorizationSettingEntity.type, type)
                .and(AuthorizationSettingEntity.settingFor, settingFor)
                .single();
    }

    @Override
    @CacheEvict(allEntries = true)
    public String insert(AuthorizationSettingEntity entity) {
        entity.setStatus(DataStatus.STATUS_ENABLED);
        String id = super.insert(entity);
        if (entity.getMenus() != null) {
            TreeSupportEntity.forEach(entity.getMenus(), menu -> {
                menu.setStatus(DataStatus.STATUS_ENABLED);
                menu.setSettingId(id);
            });
            authorizationSettingMenuService.insertBatch(entity.getMenus());
        }
        if (entity.getDetails() != null) {
            for (AuthorizationSettingDetailEntity detail : entity.getDetails()) {
                tryValidate(detail);
                detail.setId(getIDGenerator().generate());
                detail.setSettingId(id);
                detail.setStatus(DataStatus.STATUS_ENABLED);
                authorizationSettingDetailDao.insert(detail);
            }
        }
        return id;
    }

    @Override
    @CacheEvict(allEntries = true)
    public int updateByPk(String id, AuthorizationSettingEntity entity) {
        int size = super.updateByPk(id, entity);
        if (entity.getMenus() != null) {
            authorizationSettingMenuService.deleteBySettingId(id);
            TreeSupportEntity.forEach(entity.getMenus(), menu -> {
                menu.setStatus(DataStatus.STATUS_ENABLED);
                menu.setSettingId(id);
            });
            authorizationSettingMenuService.insertBatch(entity.getMenus());
        }
        if (entity.getDetails() != null) {
            DefaultDSLDeleteService
                    .createDelete(authorizationSettingDetailDao)
                    .where(AuthorizationSettingDetailEntity.settingId, id)
                    .exec();
            for (AuthorizationSettingDetailEntity detail : entity.getDetails()) {
                detail.setSettingId(id);
                detail.setStatus(DataStatus.STATUS_ENABLED);
                authorizationSettingDetailDao.insert(detail);
            }
        }
        return size;
    }

    @Override
    @CacheEvict(allEntries = true)
    public int deleteByPk(String id) {
        authorizationSettingMenuService.deleteBySettingId(id);
        return super.deleteByPk(id);
    }

    private List<AuthorizationSettingEntity> getUserSetting(String userId) {
        Map<String, List<SettingInfo>> settingInfo =
                authorizationSettingTypeSuppliers.stream()
                        .map(supplier -> supplier.get(userId))
                        .flatMap(Set::stream)
                        .collect(Collectors.groupingBy(SettingInfo::getType));
        Stream<Map.Entry<String, List<SettingInfo>>> settingInfoStream = settingInfo.entrySet().stream();
        //大于1 使用并行处理
        if (settingInfo.size() > 1)
            settingInfoStream.parallel();
        return settingInfoStream
                .map(entry ->
                        createQuery()
                                // where type = ? and setting_for in (?,?,?....)
                                .where(type, entry.getKey())
                                .and()
                                .in(settingFor, entry.getValue().stream().map(SettingInfo::getSettingFor).collect(Collectors.toList()))
                                .list())
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(key = "'user-menu-list:'+#userId")
    public List<UserMenuEntity> getUserMenuAsList(String userId) {
        if (null == userId) return null;
        UserEntity userEntity = userService.selectByPk(userId);
        if (userEntity == null) return null;
        List<AuthorizationSettingEntity> entities = getUserSetting(userId);
        //用户持有的权限设置id集合
        List<String> settingIdList = entities.stream()
                .map(AuthorizationSettingEntity::getId)
                .collect(Collectors.toList());
        //获取全部设置的菜单
        List<AuthorizationSettingMenuEntity> menuEntities = authorizationSettingMenuService
                .selectBySettingId(settingIdList);
        //得到菜单id
        List<String> menuIdList = menuEntities.stream()
                .map(AuthorizationSettingMenuEntity::getMenuId)
                .distinct()
                .collect(Collectors.toList());
        //获取全部菜单,并创建缓存备用
        Map<String, MenuEntity> menuCache = menuService
                .selectByPk(menuIdList)
                .stream()
                .collect(Collectors.toMap(MenuEntity::getId, Function.identity()));
        //根据配置,重新构造菜单结构
        List<UserMenuEntity> reBuildMenu = new LinkedList<>();
        for (AuthorizationSettingMenuEntity entity : menuEntities) {
            MenuEntity cache = menuCache.get(entity.getId());
            if (null != cache && cache.getStatus() == 1) {
                UserMenuEntity menu = entityFactory.newInstance(UserMenuEntity.class, cache);
                menu.setSortIndex(entity.getSortIndex());
                menu.setLevel(entity.getLevel());
                menu.setId(entity.getId());
                menu.setParentId(entity.getParentId());
                menu.setMenuId(cache.getId());
                reBuildMenu.add(menu);
            }
        }
        return reBuildMenu;
    }

    @Override
    @Cacheable(key = "'menu-tree:'+#userId")
    public List<UserMenuEntity> getUserMenuAsTree(String userId) {
        return TreeSupportEntity.list2tree(getUserMenuAsList(userId), UserMenuEntity::setChildren,
                (Predicate<UserMenuEntity>) menuEntity ->
                        // parentId为空或者为-1的菜单则认为是根菜单
                        menuEntity.getParentId() == null || "-1".equals(menuEntity.getParentId()));
    }

    @Override
    public Authentication initUserAuthorization(String userId) {
        if (null == userId) return null;
        UserEntity userEntity = userService.selectByPk(userId);
        if (userEntity == null) return null;
        SimpleAuthentication authentication = new SimpleAuthentication();
        // 用户信息
        authentication.setUser(new SimpleUser(userId, userEntity.getUsername(), userEntity.getName()));
        //角色
        authentication.setRoles(userService.getUserRole(userId)
                .stream().map(role -> new SimpleRole(role.getId(), role.getName()))
                .collect(Collectors.toList()));

        List<String> settingIdList = getUserSetting(userId).stream()
                .map(AuthorizationSettingEntity::getId)
                .collect(Collectors.toList());

        if (settingIdList.isEmpty()) {
            authentication.setPermissions(new ArrayList<>());
            return authentication;
        }

        // where status=1 and setting_id in (?,?,?)
        List<AuthorizationSettingDetailEntity> detailList = DefaultDSLQueryService
                .createQuery(authorizationSettingDetailDao)
                .where(AuthorizationSettingDetailEntity.status, STATE_OK)
                .and().in(AuthorizationSettingDetailEntity.settingId, settingIdList)
                .list();
        //权限
        Map<String, List<AuthorizationSettingDetailEntity>> settings = detailList
                .stream()
                .collect(Collectors.groupingBy(AuthorizationSettingDetailEntity::getPermissionId));
        List<Permission> permissions = new ArrayList<>();
        settings.forEach((permissionId, details) -> {
            SimplePermission permission = new SimplePermission();
            permission.setId(permissionId);
            Set<String> actions = new HashSet<>();
            Set<DataAccessConfig> dataAccessConfigs = new HashSet<>();
            //排序,根据优先级进行排序
            Collections.sort(details);
            for (AuthorizationSettingDetailEntity detail : details) {
                //如果指定不合并相同的配置,则清空之前的配置
                if (!Boolean.TRUE.equals(detail.isMerge())) {
                    actions.clear();
                    dataAccessConfigs.clear();
                }
                // actions
                if (null != detail.getActions()) {
                    actions.addAll(detail.getActions());
                }
                // 数据权限控制配置
                if (null != detail.getDataAccesses()) {
                    dataAccessConfigs.addAll(detail.getDataAccesses()
                            .stream()
                            .map(dataAccessFactory::create)
                            .collect(Collectors.toSet()));
                }
            }
            permission.setActions(actions);
            permission.setDataAccesses(dataAccessConfigs);
            permissions.add(permission);
        });
        authentication.setPermissions(permissions);
        return authentication;
    }


    @Autowired
    public void setDataAccessFactory(DataAccessFactory dataAccessFactory) {
        this.dataAccessFactory = dataAccessFactory;
    }

    @Autowired
    public void setAuthorizationSettingTypeSuppliers(List<AuthorizationSettingTypeSupplier> authorizationSettingTypeSuppliers) {
        this.authorizationSettingTypeSuppliers = authorizationSettingTypeSuppliers;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Autowired
    public void setAuthorizationSettingDao(AuthorizationSettingDao authorizationSettingDao) {
        this.authorizationSettingDao = authorizationSettingDao;
    }

    @Autowired
    public void setAuthorizationSettingDetailDao(AuthorizationSettingDetailDao authorizationSettingDetailDao) {
        this.authorizationSettingDetailDao = authorizationSettingDetailDao;
    }

    @Autowired
    public void setAuthorizationSettingMenuService(AuthorizationSettingMenuService authorizationSettingMenuService) {
        this.authorizationSettingMenuService = authorizationSettingMenuService;
    }

    @Autowired
    public void setMenuService(MenuService menuService) {
        this.menuService = menuService;
    }
}
