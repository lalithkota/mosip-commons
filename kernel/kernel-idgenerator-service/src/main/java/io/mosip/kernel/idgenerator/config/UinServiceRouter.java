package io.mosip.kernel.idgenerator.config;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.io.IOException;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.mosip.kernel.core.authmanager.authadapter.spi.VertxAuthenticationProvider;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.signatureutil.exception.SignatureUtilClientException;
import io.mosip.kernel.core.signatureutil.exception.SignatureUtilException;
import io.mosip.kernel.core.signatureutil.model.SignatureResponse;
import io.mosip.kernel.core.signatureutil.spi.SignatureUtil;
import io.mosip.kernel.uingenerator.constant.UinGeneratorConstant;
import io.mosip.kernel.uingenerator.constant.UinGeneratorErrorCode;
import io.mosip.kernel.uingenerator.dto.GetBulkUinsRequestDto;
import io.mosip.kernel.uingenerator.dto.GetBulkUinsResponseDto;
import io.mosip.kernel.uingenerator.dto.UinResponseDto;
import io.mosip.kernel.uingenerator.dto.UinStatusUpdateReponseDto;
import io.mosip.kernel.uingenerator.dto.UpdateBulkUinsStatusResponseDto;
import io.mosip.kernel.uingenerator.entity.UinEntity;
import io.mosip.kernel.uingenerator.exception.UinNotFoundException;
import io.mosip.kernel.uingenerator.exception.UinNotIssuedException;
import io.mosip.kernel.uingenerator.exception.UinStatusNotFoundException;
import io.mosip.kernel.uingenerator.service.UinService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * Router for vertx server
 * 
 * @author Dharmesh Khandelwal
 * @author Urvil Joshi
 * @author Megha Tanga
 * @author Raj Jha
 * @since 1.0.0
 *
 */
@Component
public class UinServiceRouter {

	/**
	 * Field for environment
	 */
	@Autowired
	Environment environment;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	private VertxAuthenticationProvider authHandler;

	@Autowired
	private SignatureUtil signatureUtil;

	/**
	 * Field for UinGeneratorService
	 */
	@Autowired
	private UinService uinGeneratorService;

	private Logger LOGGER = LoggerFactory.getLogger(UinServiceRouter.class);

	@Value("${"+ UinGeneratorConstant.GET_EXECUTOR_POOL_ENABLE + ":400}")
	private int workerExecutorPool;

	@Value("${"+ UinGeneratorConstant.SIGNING_ENABLE + ":false}")
	private boolean isSignEnable;

	@Value("${mosip.kernel.uin.bulk.enabled:true}")
	private boolean isBulkEnabled;

	@Value("${mosip.kernel.uin.auth.get-uin.enabled:${mosip.kernel.uin.auth.enabled:true}}")
	private boolean isGetUinAuthEnabled;

	@Value("${mosip.kernel.uin.auth.put-uin.enabled:${mosip.kernel.uin.auth.enabled:true}}")
	private boolean isPutUinAuthEnabled;

	@Value("${mosip.kernel.uin.auth.get-bulk-uins.enabled:${mosip.kernel.uin.auth.enabled:true}}")
	private boolean isGetBulkUinsAuthEnabled;

	@Value("${mosip.kernel.uin.auth.put-bulk-uins.enabled:${mosip.kernel.uin.auth.enabled:true}}")
	private boolean isPutBulkUinsAuthEnabled;

	@Value("${mosip.kernel.uin.min-unused-threshold}")
	private long thresholdUinCount;

	/**
	 * Creates router for vertx server
	 * 
	 * @param vertx vertx
	 * @return Router
	 */
	public Router createRouter(Vertx vertx) {
		Router router = Router.router(vertx);

		LOGGER.info("worker executor pool {}", workerExecutorPool);

		router.route().handler(routingContext -> {
			routingContext.response().headers().add(CONTENT_TYPE, UinGeneratorConstant.APPLICATION_JSON);
			routingContext.next();
		});

		if (isGetUinAuthEnabled){
			authHandler.addAuthFilter(router, "/", HttpMethod.GET, "ID_REPOSITORY");
		}
		router.get("/").handler(this::getRouter);
		if (isPutUinAuthEnabled){
			authHandler.addAuthFilter(router, "/", HttpMethod.PUT, "ID_REPOSITORY");
		}
		router.route().handler(BodyHandler.create());
		router.put("/").consumes(UinGeneratorConstant.APPLICATION_JSON).handler(this::updateRouter);

		if(isBulkEnabled){
			if (isGetBulkUinsAuthEnabled){
				authHandler.addAuthFilter(router, "/bulk", HttpMethod.POST, "ID_REPOSITORY");
			}
			router.post("/bulk").handler(this::getBulkHandler);
			if (isPutBulkUinsAuthEnabled){
				authHandler.addAuthFilter(router, "/bulk", HttpMethod.PUT, "ID_REPOSITORY");
			}
			router.put("/bulk").consumes(UinGeneratorConstant.APPLICATION_JSON).handler(this::updateBulkHandler);
		}

		router.route("/" + UinGeneratorConstant.SWAGGER_UI_PATH + "/*").handler(
				StaticHandler.create().setCachingEnabled(false).setWebRoot(UinGeneratorConstant.SWAGGER_UI_PATH)
						.setAlwaysAsyncFS(true).setAllowRootFileSystemAccess(true));
		return router;
	}

	private void getRouter(RoutingContext routingContext) {
		ResponseWrapper<UinResponseDto> reswrp = new ResponseWrapper<>();
		WorkerExecutor executor = routingContext.vertx().createSharedWorkerExecutor("get-uin", workerExecutorPool);
		executor.executeBlocking(blockingCodeHandler -> {
			try {
				checkAndGenerateUins(routingContext.vertx());
				UinResponseDto uin = new UinResponseDto();
				uin = uinGeneratorService.getUin(routingContext);
				reswrp.setResponsetime(OffsetDateTime.now().toLocalDateTime());
				reswrp.setResponse(uin);
				reswrp.setErrors(null);
				blockingCodeHandler.complete();
			} catch (UinNotFoundException e) {
				ServiceError error = new ServiceError(UinGeneratorErrorCode.UIN_NOT_FOUND.getErrorCode(),
						UinGeneratorErrorCode.UIN_NOT_FOUND.getErrorMessage());
				setError(routingContext, error, blockingCodeHandler);
			}
			/*
			 * catch (Exception e) { ExceptionUtils.logRootCause(e); ServiceError error =
			 * new ServiceError(UinGeneratorErrorCode.INTERNAL_SERVER_ERROR.getErrorCode(),
			 * e.getMessage()); setError(routingContext, error, blockingCodeHandler); }
			 */
		}, false, resultHandler -> {
			if (resultHandler.succeeded()) {
				if (isSignEnable) {
					signAndUpdateResponse(routingContext, reswrp);
				}
				try {
					routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
							.setStatusCode(200).end(objectMapper.writeValueAsString(reswrp));
				} catch (JsonProcessingException e) {

				}
			} else {
				try {
					routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
							.setStatusCode(200)
							.end(objectMapper.writeValueAsString(resultHandler.cause().getMessage()));
				} catch (JsonProcessingException e1) {

				}
			}
		});

	}

	/**
	 * update router for update the status of the given UIN
	 * 
	 * @param RoutingContext routingContext
	 */
	private void updateRouter(RoutingContext routingContext) {
		UinStatusUpdateReponseDto uinresponse = null;
		UinEntity uin;
		RequestWrapper<UinEntity> reqwrp;
		try {
			reqwrp = objectMapper.readValue(routingContext.getBodyAsJson().toString(),
					new TypeReference<RequestWrapper<UinEntity>>() {
					});
			uin = reqwrp.getRequest();
		} catch (Exception e) {
			ServiceError error = new ServiceError(UinGeneratorErrorCode.INTERNAL_SERVER_ERROR.getErrorCode(),
					e.getMessage());
			setError(routingContext, error);
			return;
		}

		if (uin == null) {
			routingContext.response().setStatusCode(200).end();
			return;
		}
		try {
			uinresponse = uinGeneratorService.updateUinStatus(uin, routingContext);
			ResponseWrapper<UinStatusUpdateReponseDto> reswrp = new ResponseWrapper<>();
			reswrp.setResponse(uinresponse);
			reswrp.setId(reqwrp.getId());
			reswrp.setVersion(reqwrp.getVersion());
			reswrp.setErrors(null);
			routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
					.setStatusCode(200).end(objectMapper.writeValueAsString(reswrp));
		} catch (UinNotFoundException e) {
			ExceptionUtils.logRootCause(e);
			ServiceError error = new ServiceError(UinGeneratorErrorCode.UIN_NOT_FOUND.getErrorCode(),
					UinGeneratorErrorCode.UIN_NOT_FOUND.getErrorMessage());
			setError(routingContext, error, reqwrp);
		} catch (UinStatusNotFoundException e) {
			ServiceError error = new ServiceError(UinGeneratorErrorCode.UIN_STATUS_NOT_FOUND.getErrorCode(),
					UinGeneratorErrorCode.UIN_STATUS_NOT_FOUND.getErrorMessage());
			setError(routingContext, error, reqwrp);
		} catch (UinNotIssuedException e) {
			ServiceError error = new ServiceError(UinGeneratorErrorCode.UIN_NOT_ISSUED.getErrorCode(),
					UinGeneratorErrorCode.UIN_NOT_ISSUED.getErrorMessage());
			setError(routingContext, error, reqwrp);
		} catch (Exception e) {
			ExceptionUtils.logRootCause(e);
			ServiceError error = new ServiceError(UinGeneratorErrorCode.INTERNAL_SERVER_ERROR.getErrorCode(),
					e.getMessage());
			setError(routingContext, error, reqwrp);
		}

	}

	/**
	 * handler for getting uins in bulk
	 *
	 * @param RoutingContext routingContext
	 */
	private void getBulkHandler(RoutingContext routingContext) {
		RequestWrapper<GetBulkUinsRequestDto> reqwrp;
		int count;
		try {
			reqwrp = objectMapper.readValue(routingContext.getBodyAsJson().toString(),
					new TypeReference<RequestWrapper<GetBulkUinsRequestDto>>() {
					});
			count = reqwrp.getRequest().getCount();
		} catch (Exception e) {
			ServiceError error = new ServiceError(UinGeneratorErrorCode.INTERNAL_SERVER_ERROR.getErrorCode(), e.getMessage());
			setError(routingContext, error);
			return;
		}
		if (count < 1 || count >= thresholdUinCount) {
			ServiceError error = new ServiceError(UinGeneratorErrorCode.BULK_UIN_INVALID_COUNT.getErrorCode(),
					UinGeneratorErrorCode.BULK_UIN_INVALID_COUNT.getErrorMessage());
			setError(routingContext, error);
		}

		ResponseWrapper<GetBulkUinsResponseDto> reswrp = new ResponseWrapper<>();
		WorkerExecutor executor = routingContext.vertx().createSharedWorkerExecutor("get-uin", workerExecutorPool);
		executor.executeBlocking(blockingCodeHandler -> {
			try {
				checkAndGenerateUins(routingContext.vertx());
				GetBulkUinsResponseDto uins = uinGeneratorService.getUinsInBulk(routingContext, count);
				reswrp.setResponsetime(OffsetDateTime.now().toLocalDateTime());
				reswrp.setResponse(uins);
				reswrp.setErrors(null);
				blockingCodeHandler.complete();
			} catch (UinNotFoundException e) {
				ServiceError error = new ServiceError(UinGeneratorErrorCode.UIN_NOT_FOUND.getErrorCode(),
						UinGeneratorErrorCode.UIN_NOT_FOUND.getErrorMessage());
				setError(routingContext, error, blockingCodeHandler);
			}
		}, false, resultHandler -> {
			if (resultHandler.succeeded()) {
				if (isSignEnable) {
					signAndUpdateResponse(routingContext, reswrp);
				}
				try {
					routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
							.setStatusCode(200).end(objectMapper.writeValueAsString(reswrp));
				} catch (JsonProcessingException e) {

				}
			} else {
				try {
					routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
							.setStatusCode(200)
							.end(objectMapper.writeValueAsString(resultHandler.cause().getMessage()));
				} catch (JsonProcessingException e1) {

				}
			}
		});

	}

	/**
	 * handler for bulk update request
	 *
	 * @param RoutingContext routingContext
	 */
	private void updateBulkHandler(RoutingContext routingContext) {
		UpdateBulkUinsStatusResponseDto uinsStatus;
		RequestWrapper<UpdateBulkUinsStatusResponseDto> reqwrp;
		try {
			reqwrp = objectMapper.readValue(routingContext.getBodyAsJson().toString(),
					new TypeReference<RequestWrapper<UpdateBulkUinsStatusResponseDto>>() {
					});
			uinsStatus = reqwrp.getRequest();
		} catch (Exception e) {
			ServiceError error = new ServiceError(UinGeneratorErrorCode.INTERNAL_SERVER_ERROR.getErrorCode(),
					e.getMessage());
			setError(routingContext, error);
			return;
		}

		if (uinsStatus == null || uinsStatus.getUins().isEmpty()) {
			routingContext.response().setStatusCode(200).end();
			return;
		}
		if(!UinGeneratorConstant.UNUSED.equals(uinsStatus.getStatus()) && !UinGeneratorConstant.ISSUED.equals(uinsStatus.getStatus()) && !UinGeneratorConstant.ASSIGNED.equals(uinsStatus.getStatus())){
			routingContext.response().setStatusCode(400).end();
			return;
		}

		ResponseWrapper<UpdateBulkUinsStatusResponseDto> reswrp = new ResponseWrapper<>();
		try {
			reswrp.setResponse(uinGeneratorService.updateUinsStatusInBulk(uinsStatus, routingContext));
			reswrp.setResponsetime(OffsetDateTime.now().toLocalDateTime());
			reswrp.setErrors(null);
			routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
					.setStatusCode(200).end(objectMapper.writeValueAsString(reswrp));
		} catch (JsonProcessingException e) {
			ExceptionUtils.logRootCause(e);
			ServiceError error = new ServiceError(UinGeneratorErrorCode.INTERNAL_SERVER_ERROR.getErrorCode(),
					e.getMessage());
			setError(routingContext, error, reqwrp);
		}
	}

	/**
	 * sign the response and return
	 *
	 * @param RoutingContext routingContext
	 */
	private void signAndUpdateResponse(RoutingContext routingContext, Object response){
		String signedData = null;
		String resWrpJsonString = null;
		SignatureResponse cryptoManagerResponseDto = null;
		try {
			resWrpJsonString = objectMapper.writeValueAsString(response);
			cryptoManagerResponseDto = signatureUtil.sign(resWrpJsonString);
		} catch (JsonProcessingException e) {

		} catch (SignatureUtilClientException e1) {
			ExceptionUtils.logRootCause(e1);
			setError(routingContext, e1.getList().get(0));
			return;
		} catch (SignatureUtilException e1) {
			ExceptionUtils.logRootCause(e1);
			ServiceError error = new ServiceError(
					UinGeneratorErrorCode.INTERNAL_SERVER_ERROR.getErrorCode(), e1.toString());
			setError(routingContext, error);
			return;
		}
		signedData = cryptoManagerResponseDto.getData();
		routingContext.response().putHeader("response-signature", signedData);
	}

	/**
	 * Checks and generate uins
	 * 
	 * @param vertx vertx
	 */
	public void checkAndGenerateUins(Vertx vertx) {
		vertx.eventBus().publish(UinGeneratorConstant.UIN_GENERATOR_ADDRESS, UinGeneratorConstant.GENERATE_UIN);
	}

	private void setError(RoutingContext routingContext, ServiceError error) {
		ResponseWrapper<ServiceError> errorResponse = new ResponseWrapper<>();
		errorResponse.getErrors().add(error);
		objectMapper.registerModule(new JavaTimeModule());
		JsonNode reqNode;
		if (routingContext.getBodyAsJson() != null) {
			try {
				reqNode = objectMapper.readTree(routingContext.getBodyAsJson().toString());
				errorResponse.setId(reqNode.path("id").asText());
				errorResponse.setVersion(reqNode.path("version").asText());
			} catch (IOException e) {
			}
		}
		try {
			routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
					.setStatusCode(200).end(objectMapper.writeValueAsString(errorResponse));
		} catch (JsonProcessingException e1) {

		}
	}

	private <T> void setError(RoutingContext routingContext, ServiceError error, RequestWrapper<T> reqwrp) {
		ResponseWrapper<ServiceError> errorResponse = new ResponseWrapper<>();
		errorResponse.getErrors().add(error);
		errorResponse.setId(reqwrp.getId());
		errorResponse.setVersion(reqwrp.getVersion());
		try {
			routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
					.setStatusCode(200).end(objectMapper.writeValueAsString(errorResponse));
		} catch (JsonProcessingException e1) {

		}
	}

	private void setError(RoutingContext routingContext, ServiceError error, Future<Object> blockingHandler) {
		ResponseWrapper<ServiceError> errorResponse = new ResponseWrapper<>();
		errorResponse.getErrors().add(error);
		objectMapper.registerModule(new JavaTimeModule());
		JsonNode reqNode;
		if (routingContext.getBodyAsJson() != null) {
			try {
				reqNode = objectMapper.readTree(routingContext.getBodyAsJson().toString());
				errorResponse.setId(reqNode.path("id").asText());
				errorResponse.setVersion(reqNode.path("version").asText());
			} catch (IOException e) {
			}
		}
		try {
			routingContext.response().putHeader("content-type", UinGeneratorConstant.APPLICATION_JSON)
					.setStatusCode(200).end(objectMapper.writeValueAsString(errorResponse));
			blockingHandler.fail(objectMapper.writeValueAsString(errorResponse));
		} catch (JsonProcessingException e1) {

		}
	}

}
