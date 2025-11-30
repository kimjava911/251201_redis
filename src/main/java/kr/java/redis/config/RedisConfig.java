package kr.java.redis.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import kr.java.redis.model.entity.ChatMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate; // New Import
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Spring Data Redis 설정을 담당하는 Configuration 클래스
 */
@Configuration
public class RedisConfig {

    /**
     * Java 8 Date/Time 타입을 처리하기 위해 JavaTimeModule이 등록된 ObjectMapper를 Bean으로 정의합니다.
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();

        // Instant 타입을 위한 커스텀 직렬화/역직렬화 모듈 생성
        SimpleModule instantModule = new SimpleModule();
        instantModule.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant instant, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                // 항상 'Z'가 포함된 ISO_INSTANT 형식으로 직렬화
                jsonGenerator.writeString(DateTimeFormatter.ISO_INSTANT.format(instant));
            }
        });
        instantModule.addDeserializer(Instant.class, new JsonDeserializer<>() {
            @Override
            public Instant deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
                // ISO-8601 형식의 문자열을 Instant 객체로 파싱
                return Instant.parse(jsonParser.getText());
            }
        });

        // 커스텀 모듈을 기본 모듈보다 먼저 등록하여 Instant 타입에 대한 처리를 우선 적용합니다.
        mapper.registerModule(instantModule);
        mapper.registerModule(new JavaTimeModule());

        // 날짜/시간 객체를 숫자 배열(타임스탬프) 대신 ISO 8601 문자열로 직렬화하도록 강제합니다.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * String 기반의 Key/Value 작업을 위한 RedisTemplate 설정 (닉네임 관리 등에 사용)
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    /**
     * ChatMessage 객체를 위한 RedisTemplate 설정
     * ObjectMapper를 Jackson2JsonRedisSerializer의 생성자를 통해 주입합니다.
     */
    @Bean
    public RedisTemplate<String, ChatMessage> chatMessageRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) { // ObjectMapper를 주입받음

        RedisTemplate<String, ChatMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 직렬화 설정
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 직렬화 설정: 생성자 주입 방식으로 ObjectMapper를 설정합니다.

        // 1. ObjectMapper의 TypeFactory를 사용하여 ChatMessage.class에 대한 JavaType을 생성
        JavaType javaType = redisObjectMapper.getTypeFactory().constructType(ChatMessage.class);

        // 2. ObjectMapper와 JavaType을 인수로 받는 Jackson2JsonRedisSerializer 생성자를 사용합니다.
        // 이는 deprecated된 setter를 사용하지 않는 최신 방식입니다.
        Jackson2JsonRedisSerializer<ChatMessage> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper, javaType);

        // 직렬화 설정
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}