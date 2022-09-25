package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.exception.TwitterToKafkaServiceException;
import com.microservices.demo.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import com.microservices.demo.twitter.to.kafka.service.runner.StreamRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnExpression("${twitter-to-kafka-service.enable-mock-tweets} && not ${twitter-to-kafka-service.enable-v2-tweets}")
public class MockKafkaStreamRunner implements StreamRunner {
    private static final Logger LOG = LoggerFactory.getLogger(MockKafkaStreamRunner.class);
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;
    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    private static final Random RANDOM = new Random();

    private static final String[] WORDS = {
            "lorem", "ipsum", "dolor", "sit", "amet", "consectetur",
            "adipiscing", "elit", "curabitur", "vel", "hendrerit", "libero",
            "eleifend", "blandit", "nunc", "ornare", "odio", "ut",
            "orci", "gravida", "imperdiet", "nullam", "purus", "lacinia",
            "a", "pretium", "quis", "congue", "praesent", "sagittis",
            "laoreet", "auctor", "mauris", "non", "velit", "eros",
            "dictum", "proin", "accumsan", "sapien", "nec", "massa",
            "volutpat", "venenatis", "sed", "eu", "molestie", "lacus",
    };

    private static final String tweetAsRawJson = "{" +
            "\"created_at\":\"{0}\"," +
            "\"id\":\"{1}\"," +
            "\"text\":\"{2}\"," +
            "\"user\":{\"id\":\"{3}\"}" +
            "}";

    public MockKafkaStreamRunner(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData, TwitterKafkaStatusListener twitterKafkaStatusListener) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
    }

    private static final String TWITTER_STATUS_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";
    @Override
    public void start() throws TwitterException {
        String[] keywords = twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0]);
        int minTweetsLength = twitterToKafkaServiceConfigData.getMockMinTweetLength();
        int maxTweetsLength = twitterToKafkaServiceConfigData.getMockMaxTweetLength();
        long sleepTimeMs = twitterToKafkaServiceConfigData.getMockSleepMs();

        LOG.info("Starting mock filtering twitter streams for keywords: {}", Arrays.toString(keywords));

        simulateTwitterStream(keywords, minTweetsLength, maxTweetsLength, sleepTimeMs);

    }

    private void simulateTwitterStream(String[] keywords, int minTweetsLength, int maxTweetsLength, long sleepTimeMs) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                while (true) {
                    String formatterTweetAsRawJson = getFormattedTweet(keywords, minTweetsLength, maxTweetsLength);
                    Status status = TwitterObjectFactory.createStatus(formatterTweetAsRawJson);
                    twitterKafkaStatusListener.onStatus(status);
                    sleep(sleepTimeMs);
                }
            }catch (TwitterException e){
                LOG.error("ERROR creating twitter status", e);
            }
        });


    }

    private void sleep(long sleepTimeMs) {
        try{
            Thread.sleep(sleepTimeMs);
        }catch (InterruptedException ex){
            throw new TwitterToKafkaServiceException("Error while sleeping for waiting new status to craete!",ex);
        }
    }

    private String getFormattedTweet(String[] keywords, int minTweetsLength, int maxTweetsLength) {
        String[] params = new String[] {
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),
                String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE)),
                getRandomTweetContent(keywords, minTweetsLength, maxTweetsLength),
                String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE))
        };

        return formatTweetAsJsonWithParams(params);
    }

    private static String formatTweetAsJsonWithParams(String[] params) {
        String tweet = tweetAsRawJson;

        for(int i = 0; i < params.length ; i++){
            tweet = tweet.replace("{" + i  + "}", params[i]);
        }

        return tweet;
    }

    private String getRandomTweetContent(String[] keywords, int minTweetsLength, int maxTweetsLength) {
        StringBuilder tweet = new StringBuilder();
        int tweetLength = RANDOM.nextInt(maxTweetsLength - minTweetsLength + 1 ) + minTweetsLength;

        return constructRandomTweet(keywords, tweet, tweetLength);
    }

    private static String constructRandomTweet(String[] keywords, StringBuilder tweet, int tweetLength) {
        for(int i = 0; i < tweetLength; i++){
            tweet.append(WORDS[RANDOM.nextInt(WORDS.length)]).append(" ");
            if(i == tweetLength / 2 ){
                tweet.append(keywords[RANDOM.nextInt(keywords.length)]).append(" ");
            }
        }

        return tweet.toString().trim();
    }
}
