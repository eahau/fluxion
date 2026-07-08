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
