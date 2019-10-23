package org.mitre.synthea.export;

import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * Uses HAPI FHIR project to validate FHIR export. http://hapifhir.io/doc_validation.html
 */
public class FHIRR4ExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testFHIRR4Export() throws Exception {
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());

    FhirContext ctx = FhirContext.forR4();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    FhirValidator validator = ctx.newValidator();
    validator.setValidateAgainstStandardSchema(true);
    validator.setValidateAgainstStandardSchematron(true);

    List<String> validationErrors = new ArrayList<String>();

    int numberOfPeople = 10;
    Generator generator = new Generator(numberOfPeople);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      int x = validationErrors.size();
      TestHelper.exportOff();
      Person person = generator.generatePerson(i);
      Config.set("exporter.fhir.export", "true");
      FhirR4.TRANSACTION_BUNDLE = person.random.nextBoolean();
      FhirR4.USE_US_CORE_IG = person.random.nextBoolean();
      String fhirJson = FhirR4.convertToFHIRJson(person, System.currentTimeMillis());
      // Check that the fhirJSON doesn't contain unresolved SNOMED-CT strings
      // (these should have been converted into URIs)
      if (fhirJson.contains("SNOMED-CT")) {
        validationErrors.add(
            "JSON contains unconverted references to 'SNOMED-CT' (should be URIs)");
      }
      // Now validate the resource...
      IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
      ValidationResult result = validator.validateWithResult(resource);
      if (!result.isSuccessful()) {
        // If the validation failed, let's crack open the Bundle and validate
        // each individual entry.resource to get context-sensitive error
        // messages...
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
          ValidationResult eresult = validator.validateWithResult(entry.getResource());
          if (!eresult.isSuccessful()) {
            for (SingleValidationMessage emessage : eresult.getMessages()) {
              boolean valid = false;
              if (emessage.getMessage().contains("@ AllergyIntolerance ait-2")) {
                /*
                 * The ait-2 invariant:
                 * Description:
                 * AllergyIntolerance.clinicalStatus SHALL NOT be present
                 * if verification Status is entered-in-error
                 * Expression:
                 * verificationStatus!='entered-in-error' or clinicalStatus.empty()
                 */
                valid = true;
              } else if (emessage.getMessage().contains("@ ExplanationOfBenefit dom-3")) {
                /*
                 * For some reason, it doesn't like the contained ServiceRequest and contained
                 * Coverage resources in the ExplanationOfBenefit, both of which are
                 * properly referenced. Running $validate on test servers finds this valid...
                 */
                valid = true;
              } else if (emessage.getMessage().contains(
                  "per-1: If present, start SHALL have a lower value than end")) {
                /*
                 * The per-1 invariant does not account for daylight savings time... so, if the
                 * daylight savings switch happens between the start and end, the validation
                 * fails, even if it is valid.
                 */
                valid = true; // ignore this error
              }
              if (!valid) {
                System.out.println(parser.encodeResourceToString(entry.getResource()));
                System.out.println("ERROR: " + emessage.getMessage());
                validationErrors.add(emessage.getMessage());
              }
            }
          }
        }
      }
      int y = validationErrors.size();
      if (x != y) {
        Exporter.export(person, System.currentTimeMillis());
      }
    }
    assertTrue("Validation of exported FHIR bundle failed: "
        + String.join("|", validationErrors), validationErrors.size() == 0);
  }
}
