package com.fluxion.schema.avro.spring.boot

import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.avro.AvroSchemaCodec
import com.fluxion.schema.avro.AvroSchemaDataProvider
import com.fluxion.schema.avro.AvroSchemaFieldExtractor
import com.fluxion.schema.avro.AvroSchemaParser
import com.fluxion.schema.avro.AvroSchemaValidator
import com.fluxion.schema.model.SchemaFormat
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(name = ["org.apache.avro.Schema"])
class AvroSchemaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["avroSchemaBundle"])
    fun avroSchemaBundle(): SchemaFormatBundle = SchemaFormatBundle(
        format = SchemaFormat.AVRO,
        parser = AvroSchemaParser(),
        validator = AvroSchemaValidator(),
        extractor = AvroSchemaFieldExtractor(),
        codec = AvroSchemaCodec(),
        dataProvider = AvroSchemaDataProvider()
    )
}
