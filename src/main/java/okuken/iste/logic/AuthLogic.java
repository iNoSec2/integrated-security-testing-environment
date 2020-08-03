package okuken.iste.logic;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.mybatis.dynamic.sql.SqlBuilder;

import com.google.common.collect.Lists;

import burp.ICookie;
import okuken.iste.controller.Controller;
import okuken.iste.dao.auto.AuthAccountDynamicSqlSupport;
import okuken.iste.dao.auto.AuthAccountMapper;
import okuken.iste.dao.auto.AuthConfigDynamicSqlSupport;
import okuken.iste.dao.auto.AuthConfigMapper;
import okuken.iste.dto.AuthAccountDto;
import okuken.iste.dto.AuthConfigDto;
import okuken.iste.dto.MessageChainDto;
import okuken.iste.dto.MessageChainNodeInDto;
import okuken.iste.dto.MessageRepeatDto;
import okuken.iste.dto.PayloadDto;
import okuken.iste.entity.auto.AuthAccount;
import okuken.iste.entity.auto.AuthConfig;
import okuken.iste.util.BurpUtil;
import okuken.iste.util.DbUtil;
import okuken.iste.util.SqlUtil;

public class AuthLogic {

	private static final AuthLogic instance = new AuthLogic();
	private AuthLogic() {}
	public static AuthLogic getInstance() {
		return instance;
	}

	/**
	 * insert or update.
	 */
	public void saveAuthAccount(AuthAccountDto dto) {
		String now = SqlUtil.now();
		DbUtil.withTransaction(session -> {
			AuthAccountMapper mapper = session.getMapper(AuthAccountMapper.class);

			//TODO: auto convert
			AuthAccount entity = new AuthAccount();
			entity.setId(dto.getId());
			entity.setFkProjectId(ConfigLogic.getInstance().getProjectId());
			entity.setUserId(dto.getUserId());
			entity.setPassword(dto.getPassword());
			entity.setRemark(dto.getRemark());
			entity.setSessionId(dto.getSessionId());
			entity.setPrcDate(now);

			if(entity.getId() != null) {
				mapper.updateByPrimaryKey(entity);
				return;
			}

			mapper.insert(entity);
			dto.setId(entity.getId());
		});
	}

	public List<AuthAccountDto> loadAuthAccounts() {
		List<AuthAccount> entitys =
			DbUtil.withSession(session -> {
				AuthAccountMapper mapper = session.getMapper(AuthAccountMapper.class);
				return mapper.select(c -> c
						.where(AuthAccountDynamicSqlSupport.fkProjectId, SqlBuilder.isEqualTo(ConfigLogic.getInstance().getProjectId()))
						.orderBy(AuthAccountDynamicSqlSupport.id));
			});

		return entitys.stream().map(entity -> {//TODO: auto convert
			AuthAccountDto dto = new AuthAccountDto();
			dto.setId(entity.getId());
			dto.setUserId(entity.getUserId());
			dto.setPassword(entity.getPassword());
			dto.setRemark(entity.getRemark());
			dto.setSessionId(entity.getSessionId());
			return dto;
		}).collect(Collectors.toList());
	}

	public void deleteAuthAccounts(List<AuthAccountDto> dtos) {
		DbUtil.withTransaction(session -> {
			AuthAccountMapper mapper = session.getMapper(AuthAccountMapper.class);

			dtos.forEach(dto -> { //TODO: logical delete?
				mapper.deleteByPrimaryKey(dto.getId());
			});
		});
	}

	/**
	 * insert or update.
	 */
	public void saveAuthConfig(AuthConfigDto dto) {
		String now = SqlUtil.now();
		DbUtil.withTransaction(session -> {
			var mapper = session.getMapper(AuthConfigMapper.class);

			//TODO: auto convert
			var entity = new AuthConfig();
			entity.setId(dto.getId());
			entity.setFkProjectId(ConfigLogic.getInstance().getProjectId());
			entity.setFkMessageChainId(dto.getAuthMessageChainId());
			entity.setPrcDate(now);

			if(entity.getId() != null) {
				mapper.updateByPrimaryKey(entity);
				return;
			}

			mapper.insert(entity);
			dto.setId(entity.getId());
		});

		ConfigLogic.getInstance().setAuthConfig(dto);
	}

	public AuthConfigDto loadAuthConfig() {
		var ret = DbUtil.withSession(session -> {
			var mapper = session.getMapper(AuthConfigMapper.class);
			var entityOptional = mapper.selectOne(c -> c.where(AuthConfigDynamicSqlSupport.fkProjectId, SqlBuilder.isEqualTo(ConfigLogic.getInstance().getProjectId())));
			if(entityOptional.isEmpty()) {
				return null;
			}
			var entity = entityOptional.get();

			var dto = new AuthConfigDto();
			dto.setId(entity.getId());
			dto.setAuthMessageChainId(entity.getFkMessageChainId());
			return dto;
		});

		if(ret == null) {
			return ret;
		}

		var messageChainDto = MessageChainLogic.getInstance().loadMessageChain(ret.getAuthMessageChainId());
		ret.setAuthMessageChainDto(messageChainDto);

		return ret;
	}

	public void sendLoginRequestAndSetSessionId(AuthAccountDto authAccountDto) {
		sendLoginRequestAndSetSessionId(authAccountDto, ConfigLogic.getInstance().getAuthConfig().getAuthMessageChainDto(), false);
	}
	public void sendLoginRequestAndSetSessionId(AuthAccountDto authAccountDto, MessageChainDto authMessageChainDto, boolean isTest) {
		var authChainNodeDto = authMessageChainDto.getNodes().get(0);

		MessageRepeatDto messageRepeatDto = Controller.getInstance().sendAutoRequest(
				createLoginPayload(authAccountDto, authChainNodeDto.getIns().get(0)/**/, authChainNodeDto.getIns().get(1)/**/),
				authChainNodeDto.getMessageDto());

		if(messageRepeatDto.getMessage().getResponse() == null) {
			throw new IllegalStateException("Authentication request's response is empty.");
		}

		String sessionIdCookieName = authChainNodeDto.getOuts().get(0)/**/.getParamName();
		Optional<ICookie> cookieOptional = BurpUtil.getHelpers().analyzeResponse(messageRepeatDto.getMessage().getResponse()).getCookies().stream()
				.filter(cookie -> cookie.getName().equals(sessionIdCookieName))
				.findFirst();

		if(cookieOptional.isEmpty()) {
			throw new IllegalStateException(String.format("Authentication request's response doesn't have %s.", sessionIdCookieName));
		}

		authAccountDto.setSessionId(cookieOptional.get().getValue());
		if(!isTest) {
			saveAuthAccount(authAccountDto);
		}
	}
	private List<PayloadDto> createLoginPayload(AuthAccountDto authAccountDto, MessageChainNodeInDto userIdInDto, MessageChainNodeInDto passwordInDto) {
		List<PayloadDto> ret = Lists.newArrayList();
		ret.add(new PayloadDto(userIdInDto.getParamName(), userIdInDto.getParamType(), authAccountDto.getUserId()));
		ret.add(new PayloadDto(passwordInDto.getParamName(), passwordInDto.getParamType(), authAccountDto.getPassword()));
		return ret;
	}

}
