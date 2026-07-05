package com.fluxion.schema.protobuf.spring.boot

import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.protobuf.ProtobufSchemaCodec
import com.fluxion.schema.protobuf.ProtobufSchemaDataProvider
import com.fluxion.schema.protobuf.ProtobufSchemaFieldExtractor
import com.fluxion.schema.protobuf.ProtobufSchemaParser
import com.fluxion.schema.protobuf.ProtobufSchemaValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Protobuf Schema 扩展 Spring Boot 自动装配。
 *
 * 当 classpath 存在 Protobuf Java（`com.google.protobuf.DescriptorProtos`）时
 * 注册 Protobuf 格式 Bundle。
 * 由 [com.fluxion.schema.spring.boot.FluxionSchemaAutoConfiguration] 的
 * `SchemaManager` 自动收集，无需额外编排。
 */
@Configuration
@ConditionalOnClass(name = ["com.google.protobuf.DescriptorProtos"])
class ProtobufSchemaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["protobufSchemaBundle"])
    fun protobufSchemaBundle(): SchemaFormatBundle = SchemaFormatBundle(
        format = SchemaFormat.PROTOBUF,
        parser = ProtobufSchemaParser(),
        validator = ProtobufSchemaValidator(),
        extractor = ProtobufSchemaFieldExtractor(),
        codec = ProtobufSchemaCodec(),
        dataProvider = ProtobufSchemaDataProvider()
    )
}
