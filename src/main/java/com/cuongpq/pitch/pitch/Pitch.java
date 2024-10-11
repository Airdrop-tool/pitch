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
import org.springframework.scheduling.annotation.Scheduled;
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

    record User(String id, String userId, Date startTime, Date endTime, Long coins) {
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
                long timeLeft = calculateTime(user);
                log.info("Wait " + DateUtil.toTime(timeLeft));
                Thread.sleep(timeLeft);
                claim(token);
                farmings(token);
            } else {
                log.error(res);
            }
        } catch (Exception e) {
            log.error("Fail to get farmings data.");
            log.error(e.getMessage());
        }
    }

    private long calculateTime(User user) {
        if (user == null) {
            return 5000L;
        }
        Date current = DateUtil.getCurrentDate();
        if (user.endTime != null && user.endTime.after(current)) {
            long timeLeft = user.endTime.getTime() - current.getTime();
            return (timeLeft > 0 ? timeLeft : 0) + 5000;
        }
        return 5000L;
    }

    private void claim(String token) {
        try {
            String url = "https://api.pitchtalk.app/v1/api/users/claim-farming";
            String res = HttpClientUtil.sendPost(url, new JSONObject(), getHeaders(token));
            User user = CommonUtil.fromJson(res, User.class);
            log.info("Farming. Balance: " + user.coins);
        } catch (Exception e) {
            log.error("Fail to claim.");
            log.error(e.getMessage());
        }
    }

    private Integer countRef(String token) {
        try {
            String url = "https://api.pitchtalk.app/v1/api/referral/count";
            String res = HttpClientUtil.sendGet(url, getHeaders(token));
            return Integer.valueOf(res);
        } catch (Exception e) {
            log.error("Fail to count ref.");
            log.error(e.getMessage());
            return 0;
        }
    }

    private void claimRef(String token) {
        try {
            if (countRef(token) == 0) {
                return;
            }
            String url = "https://api.pitchtalk.app/v1/api/users/claim-referral";
            String res = HttpClientUtil.sendPost(url, new JSONObject(), getHeaders(token));
            User user = CommonUtil.fromJson(res, User.class);
            log.info("Claim ref. Balance: " + user.coins);
        } catch (Exception e) {
            log.error("Fail to claim ref.");
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

    @Scheduled(cron = "0 0 0/4 ? * *")
    @EventListener(ApplicationReadyEvent.class)
    public void claimRef() {
        queryIds.forEach(e -> new Thread(() -> {
            log.info("================ Start Claim Ref Pitch ================");
            AuthData authData = auth(e);
            if (authData != null) {
                claimRef(authData.accessToken);
            } else {
                log.error("Fail to get token.");
            }
            log.info("================ End Claim Ref Pitch ================");
        }).start());
    }
}
