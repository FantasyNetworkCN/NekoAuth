package neko.fantasy.auth.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.settings.SettingsService.SmsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;

@Component
class DypnsSmsClient {

    private static final Logger log = LoggerFactory.getLogger(DypnsSmsClient.class);
    private final ObjectMapper objectMapper;

    DypnsSmsClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void send(SmsConfig config, String phone, String outId) {
        var request = new SendSmsVerifyCodeRequest()
                .setPhoneNumber(phone.substring(3))
                .setCountryCode("86")
                .setSignName(config.signName())
                .setTemplateCode(config.templateCode())
                .setTemplateParam(templateParameters(config))
                .setSchemeName(config.schemeName())
                .setOutId(outId)
                .setCodeLength(6L)
                .setCodeType(1L)
                .setValidTime(SmsRegistrationService.TTL.toSeconds())
                .setInterval(SmsRegistrationService.COOLDOWN.toSeconds())
                .setDuplicatePolicy(1L)
                .setReturnVerifyCode(false);
        try {
            var body = client(config).sendSmsVerifyCodeWithOptions(request, runtimeOptions()).getBody();
            if (body != null && Boolean.TRUE.equals(body.getSuccess()) && "OK".equals(body.getCode())) {
                return;
            }
            throw sendFailure(body == null ? null : body.getCode());
        } catch (ApiException exception) {
            throw exception;
        } catch (TeaException exception) {
            throw sendFailure(exception.getCode());
        } catch (Exception exception) {
            throw unavailable("send", exception.getClass().getSimpleName());
        }
    }

    boolean verify(SmsConfig config, String phone, String outId, String code) {
        var request = new CheckSmsVerifyCodeRequest()
                .setPhoneNumber(phone.substring(3))
                .setCountryCode("86")
                .setSchemeName(config.schemeName())
                .setOutId(outId)
                .setVerifyCode(code);
        try {
            var body = client(config).checkSmsVerifyCodeWithOptions(request, runtimeOptions()).getBody();
            if (body != null && Boolean.TRUE.equals(body.getSuccess()) && "OK".equals(body.getCode())) {
                var model = body.getModel();
                return model != null && "PASS".equals(model.getVerifyResult()) && outId.equals(model.getOutId());
            }
            throw unavailable("verify", body == null ? null : body.getCode());
        } catch (ApiException exception) {
            throw exception;
        } catch (TeaException exception) {
            throw unavailable("verify", exception.getCode());
        } catch (Exception exception) {
            throw unavailable("verify", exception.getClass().getSimpleName());
        }
    }

    private static Client client(SmsConfig config) throws Exception {
        return new Client(new Config()
                .setEndpoint("dypnsapi.aliyuncs.com")
                .setProtocol("HTTPS")
                .setAccessKeyId(config.accessKeyId())
                .setAccessKeySecret(config.accessKeySecret()));
    }

    private String templateParameters(SmsConfig config) {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put(config.codeParameter(), "##code##");
        if (config.minutesParameter() != null) {
            parameters.put(config.minutesParameter(), Long.toString(SmsRegistrationService.TTL.toMinutes()));
        }
        return objectMapper.writeValueAsString(parameters);
    }

    private static RuntimeOptions runtimeOptions() {
        return new RuntimeOptions().setAutoretry(false).setConnectTimeout(2000).setReadTimeout(3000);
    }

    static ApiException sendFailure(String code) {
        if ("biz.FREQUENCY".equals(code) || "FREQUENCY_FAIL".equals(code)
                || "isv.BUSINESS_LIMIT_CONTROL".equals(code) || "BUSINESS_LIMIT_CONTROL".equals(code)) {
            return ApiException.tooManyRequests("SMS_CODE_RATE_LIMIT", "短信发送过于频繁，请稍后再试");
        }
        if ("MOBILE_NUMBER_ILLEGAL".equals(code)) {
            return ApiException.badRequest("PHONE_INVALID", "请输入有效的中国大陆手机号");
        }
        return unavailable("send", code);
    }

    private static ApiException unavailable(String operation, String code) {
        // 供应商异常可能携带请求内容。日志只记录操作和状态码。
        log.warn("阿里云短信接口调用失败，操作={}，状态={}", operation, code);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_SERVICE_UNAVAILABLE", "短信服务暂不可用，请稍后再试");
    }
}
