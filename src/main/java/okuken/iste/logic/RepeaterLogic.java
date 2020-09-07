package okuken.iste.logic;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;

import burp.IHttpRequestResponse;
import burp.IParameter;
import burp.IRequestInfo;
import burp.IResponseInfo;
import okuken.iste.dao.auto.MessageRawMapper;
import okuken.iste.dao.auto.MessageRepeatDynamicSqlSupport;
import okuken.iste.dao.MessageRepeatMapper;
import okuken.iste.dao.auto.MessageRepeatRedirDynamicSqlSupport;
import okuken.iste.dao.auto.MessageRepeatRedirMapper;
import okuken.iste.dto.AuthAccountDto;
import okuken.iste.dto.AuthConfigDto;
import okuken.iste.dto.MessageDto;
import okuken.iste.dto.MessageRepeatDto;
import okuken.iste.dto.MessageRepeatRedirectDto;
import okuken.iste.dto.PayloadDto;
import okuken.iste.dto.burp.HttpRequestResponseMock;
import okuken.iste.dto.burp.HttpServiceMock;
import okuken.iste.entity.auto.MessageRaw;
import okuken.iste.enums.ParameterType;
import okuken.iste.entity.MessageRepeat;
import okuken.iste.util.BurpUtil;
import okuken.iste.util.DbUtil;
import okuken.iste.util.HttpUtil;
import okuken.iste.util.SqlUtil;

public class RepeaterLogic {

	private static final RepeaterLogic instance = new RepeaterLogic();

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private RepeaterLogic() {}
	public static RepeaterLogic getInstance() {
		return instance;
	}

	public MessageRepeatDto sendRequest(List<PayloadDto> payloadDtos, MessageDto orgMessageDto, Consumer<MessageRepeatDto> callback, boolean needSaveHistory) {
		return sendRequest(
				applyPayloads(orgMessageDto.getMessage().getRequest(), payloadDtos),
				null,
				orgMessageDto,
				callback,
				needSaveHistory);
	}
	private byte[] applyPayloads(byte[] request, List<PayloadDto> payloadDtos) {
		byte[] ret = request;
		for(PayloadDto payloadDto: payloadDtos) {
			ret = BurpUtil.getHelpers().updateParameter(ret, BurpUtil.getHelpers().buildParameter(
					payloadDto.getTargetParamName(),
					payloadDto.getPayload(),
					payloadDto.getTargetParamType()));
		}
		return ret;
	}

	public MessageRepeatDto sendRequest(byte[] aRequest, AuthAccountDto authAccountDto, MessageDto orgMessageDto, Consumer<MessageRepeatDto> callback, boolean needSaveHistory) {
		byte[] request = applyAuthAccount(aRequest, authAccountDto);

		MessageRepeatDto repeatDto = new MessageRepeatDto();
		repeatDto.setOrgMessageId(orgMessageDto.getId());
		repeatDto.setMessage(new HttpRequestResponseMock(request, null, orgMessageDto.getMessage().getHttpService()));
		repeatDto.setSendDate(Calendar.getInstance().getTime());
		repeatDto.setDifference("");//TODO: impl
		if(authAccountDto != null && authAccountDto.getSessionId() != null) {
			repeatDto.setUserId(authAccountDto.getUserId());
		}

		if(needSaveHistory) {
			save(repeatDto);
		}

		executorService.submit(() -> {
			try {
				long timerStart = System.currentTimeMillis();
	
				IHttpRequestResponse response = BurpUtil.getCallbacks().makeHttpRequest(
						orgMessageDto.getMessage().getHttpService(),
						request);
	
				long timerEnd = System.currentTimeMillis();
				int time = (int) (timerEnd - timerStart);
	
				repeatDto.setMessage(response);
				repeatDto.setStatus(response.getResponse() != null ? BurpUtil.getHelpers().analyzeResponse(response.getResponse()).getStatusCode() : -1);
				repeatDto.setLength(response.getResponse() != null ? response.getResponse().length : 0);
				repeatDto.setTime(time);
	
				if(needSaveHistory) {
					updateResponse(repeatDto);
				}
	
				if(callback != null) {
					callback.accept(repeatDto);
				}

			} catch(Exception e) {
				BurpUtil.printStderr(e);
				throw e;
			}
		});

		return repeatDto;
	}
	private byte[] applyAuthAccount(byte[] request, AuthAccountDto authAccountDto) {
		if(authAccountDto == null || authAccountDto.getSessionId() == null) {
			return request;
		}

		AuthConfigDto authConfig = ConfigLogic.getInstance().getAuthConfig();
		if(authConfig == null) {
			throw new IllegalStateException("Sessionid was set but AuthConfig has not saved.");
		}
		var sessionidNodeOutDto = authConfig.getAuthMessageChainDto().getNodes().get(0).getOuts().get(0);

		IParameter sessionIdParam = BurpUtil.getHelpers().buildParameter(
				sessionidNodeOutDto.getParamName(),
				authAccountDto.getSessionId(),
				sessionidNodeOutDto.getParamType());

		return applyParameter(request, sessionIdParam);
	}
	private byte[] applyParameter(byte[] request, IParameter parameter) {
		var parameterType = ParameterType.getById(parameter.getType());
		switch (parameterType) {
			case COOKIE:
				var ret = BurpUtil.getHelpers().removeParameter(request, parameter);
				ret = HttpUtil.removeDustAtEndOfCookieHeader(ret); // bug recovery
				ret = BurpUtil.getHelpers().addParameter(ret, parameter);
				return ret;
			case JSON:
				//TODO: generalize...
				var authorizationHeader = HttpUtil.createAuthorizationBearerHeader(parameter.getValue());

				var requestInfo = BurpUtil.getHelpers().analyzeRequest(request);
				var headers = requestInfo.getHeaders();
				var authorizationHeaderIndex = IntStream.range(0, headers.size()).filter(i -> HttpUtil.judgeIsAuthorizationBearerHeader(headers.get(i))).findFirst();
				if(authorizationHeaderIndex.isPresent()) {
					headers.remove(authorizationHeaderIndex.getAsInt());
					headers.add(authorizationHeaderIndex.getAsInt(), authorizationHeader);
				} else {
					headers.add(authorizationHeader);
				}

				var body = HttpUtil.extractMessageBody(request, requestInfo.getBodyOffset());
				return BurpUtil.getHelpers().buildHttpMessage(headers, body);
			default:
				throw new IllegalArgumentException(String.format("Unsupported parameter type: %s", parameterType));
		}
	}

	private void save(MessageRepeatDto messageRepeatDto) {
		String now = SqlUtil.now();
		DbUtil.withTransaction(session -> {
			MessageRawMapper messageRawMapper = session.getMapper(MessageRawMapper.class);
			MessageRepeatMapper messageRepeatMapper = session.getMapper(MessageRepeatMapper.class);

			//TODO: auto convert
			MessageRaw messageRaw = new MessageRaw();
			messageRaw.setHost(messageRepeatDto.getMessage().getHttpService().getHost());
			messageRaw.setPort(messageRepeatDto.getMessage().getHttpService().getPort());
			messageRaw.setProtocol(messageRepeatDto.getMessage().getHttpService().getProtocol());
			messageRaw.setRequest(messageRepeatDto.getMessage().getRequest());
			messageRaw.setResponse(messageRepeatDto.getMessage().getResponse());
			messageRaw.setPrcDate(now);
			messageRawMapper.insert(messageRaw);
			messageRepeatDto.setMessageRawId(messageRaw.getId());

			MessageRepeat messageRepeat = new MessageRepeat();
			messageRepeat.setFkMessageId(messageRepeatDto.getOrgMessageId());
			messageRepeat.setFkMessageRawId(messageRaw.getId());
			messageRepeat.setSendDate(SqlUtil.dateToString(messageRepeatDto.getSendDate()));
			messageRepeat.setDifference(messageRepeatDto.getDifference());
			messageRepeat.setUserId(messageRepeatDto.getUserId());
			messageRepeat.setTime(messageRepeatDto.getTime());
			messageRepeat.setStatus(messageRepeatDto.getStatus());
			messageRepeat.setLength(messageRepeatDto.getLength());
			messageRepeat.setPrcDate(now);
			messageRepeatMapper.insert(messageRepeat);
			messageRepeatDto.setId(messageRepeat.getId());
		});
	}

	private void updateResponse(MessageRepeatDto messageRepeatDto) {
		String now = SqlUtil.now();
		DbUtil.withTransaction(session -> {
			MessageRawMapper messageRawMapper = session.getMapper(MessageRawMapper.class);
			MessageRepeatMapper messageRepeatMapper = session.getMapper(MessageRepeatMapper.class);

			MessageRaw messageRaw = new MessageRaw();
			messageRaw.setId(messageRepeatDto.getMessageRawId());
			messageRaw.setResponse(messageRepeatDto.getMessage().getResponse());
			messageRaw.setPrcDate(now);
			messageRawMapper.updateByPrimaryKeySelective(messageRaw);

			MessageRepeat messageRepeat = new MessageRepeat();
			messageRepeat.setId(messageRepeatDto.getId());
			messageRepeat.setTime(messageRepeatDto.getTime());
			messageRepeat.setStatus(messageRepeatDto.getStatus());
			messageRepeat.setLength(messageRepeatDto.getLength());
			messageRepeat.setPrcDate(now);
			messageRepeatMapper.updateByPrimaryKeySelective(messageRepeat);
		});
	}

	public void updateMemo(MessageRepeatDto messageRepeatDto) {
		String now = SqlUtil.now();
		DbUtil.withTransaction(session -> {
			MessageRepeatMapper messageRepeatMapper = session.getMapper(MessageRepeatMapper.class);

			MessageRepeat messageRepeat = new MessageRepeat();
			messageRepeat.setId(messageRepeatDto.getId());
			messageRepeat.setMemo(messageRepeatDto.getMemo());
			messageRepeat.setPrcDate(now);
			messageRepeatMapper.updateByPrimaryKeySelective(messageRepeat);
		});
	}

	public List<MessageRepeatDto> loadHistory(Integer orgMessageId) {
		List<MessageRepeat> messageRepeats = 
			DbUtil.withSession(session -> {
				var messageRepeatMapper = session.getMapper(MessageRepeatMapper.class);
				SelectStatementProvider selectStatement = SqlBuilder
								.select(ArrayUtils.addAll(
									Arrays.stream(MessageRepeatMapper.selectList).map(c->c.as(((SqlColumn<?>)c).name())).collect(Collectors.toList()).toArray(new BasicColumn[0]),
									Arrays.stream(MessageRepeatRedirMapper.selectList).map(c->c.as("mrr_" + ((SqlColumn<?>)c).name())).collect(Collectors.toList()).toArray(new BasicColumn[0])))
								.from(MessageRepeatDynamicSqlSupport.messageRepeat)
								.leftJoin(MessageRepeatRedirDynamicSqlSupport.messageRepeatRedir).on(MessageRepeatDynamicSqlSupport.messageRepeat.id, SqlBuilder.equalTo(MessageRepeatRedirDynamicSqlSupport.messageRepeatRedir.fkMessageRepeatId))
								.where(MessageRepeatDynamicSqlSupport.fkMessageId, SqlBuilder.isEqualTo(orgMessageId))
								.orderBy(MessageRepeatDynamicSqlSupport.id)
								.build()
								.render(RenderingStrategies.MYBATIS3);

				return messageRepeatMapper.selectManyWithRedir(selectStatement);
			});

		return messageRepeats.stream().map(messageRepeat -> { //TODO:converter
			MessageRepeatDto dto = new MessageRepeatDto();
			dto.setId(messageRepeat.getId());
			dto.setOrgMessageId(messageRepeat.getFkMessageId());
			dto.setMessageRawId(messageRepeat.getFkMessageRawId());
			dto.setSendDate(SqlUtil.stringToDate(messageRepeat.getSendDate()));
			dto.setDifference(messageRepeat.getDifference());
			dto.setUserId(messageRepeat.getUserId());
			dto.setTime(messageRepeat.getTime());
			dto.setStatus(messageRepeat.getStatus());
			dto.setLength(messageRepeat.getLength());
			dto.setMemo(messageRepeat.getMemo());

			dto.setMessageRepeatRedirectDtos(
				messageRepeat.getMessageRepeatRedirs().stream().map(redirect -> {
					var redirectDto = new MessageRepeatRedirectDto();
					redirectDto.setId(redirect.getId());
					redirectDto.setSendDate(SqlUtil.stringToDate(redirect.getSendDate()));
					redirectDto.setStatus(redirect.getStatus());
					redirectDto.setLength(redirect.getLength());
					redirectDto.setTime(redirect.getTime());
					redirectDto.setMessageRawId(redirect.getFkMessageRawId());
					return redirectDto;
				}).collect(Collectors.toList()));

			return dto;
		}).collect(Collectors.toList());
	}

	public MessageRepeatRedirectDto sendFollowRedirectRequest(byte[] aRequest, byte[] aResponse, MessageDto orgMessageDto, Consumer<MessageRepeatRedirectDto> callback) {
		var requestInfo = BurpUtil.getHelpers().analyzeRequest(aRequest);
		var responseInfo = BurpUtil.getHelpers().analyzeResponse(aResponse);
		URL redirectUrl;
		try {
			redirectUrl = new URL(extractLocationHeaderValue(responseInfo));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}

		var baseRequest = String.format("GET %s HTTP/1.1\r\n\r\n", redirectUrl.getFile()).getBytes(); 
		var request = applyCookieForRedirect(baseRequest, redirectUrl, requestInfo, responseInfo);

		// TODO: add headers

		var redirectDto = new MessageRepeatRedirectDto();
		redirectDto.setSendDate(Calendar.getInstance().getTime());

		executorService.submit(() -> {
			try {
				long timerStart = System.currentTimeMillis();
	
				IHttpRequestResponse response = BurpUtil.getCallbacks().makeHttpRequest(
						new HttpServiceMock(redirectUrl.getHost(), redirectUrl.getPort(), redirectUrl.getProtocol()),
						request);
	
				long timerEnd = System.currentTimeMillis();
				int time = (int) (timerEnd - timerStart);

				redirectDto.setMessage(response);
				redirectDto.setStatus(response.getResponse() != null ? BurpUtil.getHelpers().analyzeResponse(response.getResponse()).getStatusCode() : -1);
				redirectDto.setLength(response.getResponse() != null ? response.getResponse().length : 0);
				redirectDto.setTime(time);

				//TODO: update db

				if(callback != null) {
					callback.accept(redirectDto);
				}

			} catch(Exception e) {
				BurpUtil.printStderr(e);
				throw e;
			}
		});

		return redirectDto;
	}
	private String extractLocationHeaderValue(IResponseInfo responseInfo) {
		var locationHeaderPrefix = "Location:";

		var locationHeaders = responseInfo.getHeaders().stream().filter(header -> new String(header.getBytes()).startsWith(locationHeaderPrefix)).collect(Collectors.toList());
		if(locationHeaders.isEmpty() || locationHeaders.size() > 1) {
			throw new IllegalArgumentException(String.format("Response includes %d Location headers. It should be one.", locationHeaders.size()));
		}

		return locationHeaders.get(0).substring(locationHeaderPrefix.length()).trim();
	}
	private byte[] applyCookieForRedirect(byte[] request, URL redirectUrl, IRequestInfo beforeRequestInfo, IResponseInfo beforeResponseInfo) {
		var ret = request;
		var orgUrl = beforeRequestInfo.getUrl(); //TODO: this throw exception. should impl by compare domain and port

		if(!judgeIsSameOrigin(orgUrl, redirectUrl)) {
			return ret;
		}

		//[CAUTION] check path is impossible because request doesn't have the path attribute of cookies...
		//apply cookie params in request
		var beforeRequestCookieParams = beforeRequestInfo.getParameters().stream()
				.filter(parameter -> parameter.getType() == IParameter.PARAM_COOKIE)
				.map(cookie -> BurpUtil.getHelpers().buildParameter(cookie.getName(), cookie.getValue(), cookie.getType()))
				.collect(Collectors.toList());

		for(var cookieParam: beforeRequestCookieParams) {
			ret = BurpUtil.getHelpers().addParameter(ret, cookieParam);
		}


		//apply cookie params in response
		var beforeResponseCookieParams = beforeResponseInfo.getCookies().stream()
				.filter(cookie -> redirectUrl.getPath().startsWith(cookie.getPath()))
				.map(cookie -> BurpUtil.getHelpers().buildParameter(cookie.getName(), cookie.getValue(), IParameter.PARAM_COOKIE))
				.collect(Collectors.toList());

		for(var targetCookieParam: beforeResponseCookieParams) {
			ret = applyParameter(ret, targetCookieParam);
		}

		return ret;
	}
	private boolean judgeIsSameOrigin(URL url1, URL url2) {
		return url1.getProtocol().equals(url2.getProtocol()) &&//TODO: change to only check authority
				url1.getAuthority().equals(url2.getAuthority());
	}


	public void shutdownExecutorService() {
		try {
			executorService.shutdownNow();
		} catch(Exception e) {
			BurpUtil.printEventLog(e.getMessage());
		}
	}

}
