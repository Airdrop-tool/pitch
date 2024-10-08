package com.cuongpq.pitch.pitch;

import com.cuongpq.pitch.dto.UserDto;
import com.cuongpq.pitch.utils.CommonUtil;
import com.cuongpq.pitch.utils.DateUtil;
import com.cuongpq.pitch.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.json.JSONObject;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cuongpq.pitch.utils.CommonUtil.getUserFromQueryId;
import static com.cuongpq.pitch.utils.DateUtil.FORMAT_HOUR_MINUTE_DAY_MONTH_YEAR;
import static com.cuongpq.pitch.utils.FileUtil.readTokens;

@Service
@Slf4j
public class Pitch {

    private static final String KEY = "pitch.query-id";

    private final List<String> queryIds;

    Pitch() {
        queryIds = readTokens(KEY);
    }

    private Map<String, String> getHeaders(String token) {
        return Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer " + token,
                "Origin", "https://webapp.pitchtalk.app",
                "Referer", "https://webapp.pitchtalk.app"
        );
    }

    record User(String id, String userId, Date startTime, Date endTime) {
    }

    record AuthData(String accessToken) {
    }

    private AuthData auth(String queryId) {
        try {
            String url = "https://api.pitchtalk.app/v1/api/auth";
            UserDto user = getUserFromQueryId(queryId);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("telegramId", String.valueOf(user.getId()));
            jsonObject.put("username", user.getUsername());
            jsonObject.put("hash", queryId);
            String res = HttpClientUtil.sendPost(url, jsonObject, new HashMap<>());
            return CommonUtil.fromJson(res, AuthData.class);
        } catch (Exception e) {
            log.error("Fail to authenticate.");
            log.error(e.getMessage());
            return null;
        }
    }

    private void farmings(String token) {
        try {
            String url = "https://api.pitchtalk.app/v1/api/farmings";
            String res = HttpClientUtil.sendGet(url, getHeaders(token));
            User user = CommonUtil.fromJson(res, User.class);
            if (user != null) {
                Date current = DateUtil.getCurrentDate();
                if (user.endTime.after(current)) {
                    log.info("Next farming: " + DateUtil.toString(user.endTime, FORMAT_HOUR_MINUTE_DAY_MONTH_YEAR));
                    Thread.sleep(user.endTime.getTime() - current.getTime() + 5000);
                    claim(token);
                } else {
                    claim(token);
                }
                farmings(token);
            } else {
                log.error(res);
            }
        } catch (Exception e) {
            log.error("Fail to get farmings data.");
            log.error(e.getMessage());
        }
    }

    private void claim(String token) {
        try {
            String url = "https://api.pitchtalk.app/v1/api/users/claim-farming";
            String res = HttpClientUtil.sendPost(url, new JSONObject(), getHeaders(token));
            log.info("Claiming: " + res);
        } catch (Exception e) {
            log.error("Fail to claim.");
            log.error(e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void farmings() {
        queryIds.forEach(e -> new Thread(() -> {
            log.info("================ Start Farming Pitch ================");
            AuthData authData = auth(e);
            if (authData != null) {
                farmings(authData.accessToken);
            } else {
                log.error("Fail to get token.");
            }
            log.info("================ End Farming Pitch ================");
        }).start());
    }
}
