package fr.suivons.ingestion.core.pipeline;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import fr.suivons.ingestion.core.IngestionCoreConfiguration;

/** Application minimale pour éprouver le pipeline, comme le ferait un module ingestion-<source>. */
@SpringBootApplication
@Import(IngestionCoreConfiguration.class)
class ApplicationDeTest {
}
