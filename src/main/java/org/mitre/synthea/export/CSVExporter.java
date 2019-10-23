package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;
import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

import com.google.common.collect.Table;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Random;

import org.mitre.synthea.engine.Event;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

/**
 * Researchers have requested a simple table-based format that could easily be
 * imported into any database for analysis. Unlike other formats which export a
 * single record per patient, this format generates 9 total files, and adds
 * lines to each based on the clinical events for each patient. These files are
 * intended to be analogous to database tables, with the patient UUID being a
 * foreign key. Files include: patients.csv, encounters.csv, allergies.csv,
 * medications.csv, conditions.csv, careplans.csv, observations.csv,
 * procedures.csv, and immunizations.csv.
 */
public class CSVExporter {
  /**
   * Writer for patients.csv.
   */
  private FileWriter patients;
  /**
   * Writer for allergies.csv.
   */
  private FileWriter allergies;
  /**
   * Writer for medications.csv.
   */
  private FileWriter medications;
  /**
   * Writer for conditions.csv.
   */
  private FileWriter conditions;
  /**
   * Writer for careplans.csv.
   */
  private FileWriter careplans;
  /**
   * Writer for observations.csv.
   */
  private FileWriter observations;
  /**
   * Writer for procedures.csv.
   */
  private FileWriter procedures;
  /**
   * Writer for immunizations.csv.
   */
  private FileWriter immunizations;
  /**
   * Writer for encounters.csv.
   */
  private FileWriter encounters;
  /**
   * Writer for imaging_studies.csv
   */
  private FileWriter imagingStudies;
  /**
   * Writer for organizations.csv
   */
  private FileWriter organizations;
  /**
   * Writer for providers.csv
   */
  private FileWriter providers;

  private long pcounter;
  private long ecounter;
  /**
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();

  /**
   * Constructor for the CSVExporter - initialize the 9 specified files and store
   * the writers in fields.
   */
  private CSVExporter() {
    try {
      File output = Exporter.getOutputFolder("csv", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();

      if (Boolean.parseBoolean(Config.get("exporter.csv.folder_per_run"))) {
        // we want a folder per run, so name it based on the timestamp
        // TODO: do we want to consider names based on the current generation options?
        String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
        String subfolderName = timestamp.replaceAll("\\W+", "_"); // make sure it's filename-safe
        outputDirectory = outputDirectory.resolve(subfolderName);
        outputDirectory.toFile().mkdirs();
      }

      File patientsFile = outputDirectory.resolve("patients.csv").toFile();
      boolean append = patientsFile.exists() && Boolean.parseBoolean(Config.get("exporter.csv.append_mode"));

      File allergiesFile = outputDirectory.resolve("allergies.csv").toFile();
      File medicationsFile = outputDirectory.resolve("medications.csv").toFile();
      File conditionsFile = outputDirectory.resolve("conditions.csv").toFile();
      File careplansFile = outputDirectory.resolve("careplans.csv").toFile();
      File observationsFile = outputDirectory.resolve("observations.csv").toFile();
      File proceduresFile = outputDirectory.resolve("procedures.csv").toFile();
      File immunizationsFile = outputDirectory.resolve("immunizations.csv").toFile();
      File encountersFile = outputDirectory.resolve("encounters.csv").toFile();
      File imagingStudiesFile = outputDirectory.resolve("imaging_studies.csv").toFile();

      patients = new FileWriter(patientsFile, append);
      allergies = new FileWriter(allergiesFile, append);
      medications = new FileWriter(medicationsFile, append);
      conditions = new FileWriter(conditionsFile, append);
      careplans = new FileWriter(careplansFile, append);
      observations = new FileWriter(observationsFile, append);
      procedures = new FileWriter(proceduresFile, append);
      immunizations = new FileWriter(immunizationsFile, append);
      encounters = new FileWriter(encountersFile, append);
      imagingStudies = new FileWriter(imagingStudiesFile, append);
      pcounter = 0;
      ecounter = 0;

      File organizationsFile = outputDirectory.resolve("organizations.csv").toFile();
      File providersFile = outputDirectory.resolve("providers.csv").toFile();
      organizations = new FileWriter(organizationsFile, append);
      providers = new FileWriter(providersFile, append);

      if (!append) {
        writeCSVHeaders();
      }
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }

  /**
   * Write the headers to each of the CSV files.
   * 
   * @throws IOException if any IO error occurs
   */
  private void writeCSVHeaders() throws IOException {
    // patients.write("Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,"
    // + "PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,"
    // + "ADDRESS,CITY,STATE,COUNTY,ZIP,LAT,LON");
    patients.write("id,title,language,financial,fname,lname,mname,DOB,street,postal_code,"
        + "city,state,country_code,drivers_license,ss,occupation,phone_home,"
        + "phone_biz,phone_contact,phone_cell,pharmacy_id,status,contact_relationship,"
        + "date,sex,referrer,referrerID,providerID,ref_providerID,email,email_direct,"
        + "ethnoracial,race,ethnicity,religion,interpretter,migrantseasonal,"
        + "family_size,monthly_income,billing_note,homeless,financial_review,"
        + "pubpid,pid,genericname1,genericval1,genericname2,genericval2,hipaa_mail,"
        + "hipaa_voice,hipaa_notice,hipaa_message,hipaa_allowsms,hipaa_allowemail,"
        + "squad,fitness,referral_source,usertext1,usertext2,usertext3,usertext4,"
        + "usertext5,usertext6,usertext7,usertext8,userlist1,userlist2,userlist3,"
        + "userlist4,userlist5,userlist6,userlist7,pricelevel,regdate,contrastart,"
        + "completed_ad,ad_reviewed,vfc,mothersname,guardiansname,allow_imm_reg_use,"
        + "allow_imm_info_share,allow_health_info_ex,allow_patient_portal,deceased_date,"
        + "deceased_reason,soap_import_status,cmsportal_login,care_team,county,"
        + "industry,imm_reg_status,imm_reg_stat_effdate,publicity_code,publ_code_eff_date,"
        + "protect_indicator,prot_indi_effdate,guardianrelationship,guardiansex,"
        + "guardianaddress,guardiancity,guardianstate,guardianpostalcode,guardiancountry,"
        + "guardianphone,guardianworkphone,guardianemail");

    patients.write(NEWLINE);
    allergies.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION");
    allergies.write(NEWLINE);
    medications.write(
        "START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST,DISPENSES,TOTALCOST," + "REASONCODE,REASONDESCRIPTION");
    medications.write(NEWLINE);
    conditions.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION");
    conditions.write(NEWLINE);
    careplans.write("Id,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION");
    careplans.write(NEWLINE);
    observations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS,TYPE");
    observations.write(NEWLINE);
    procedures.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST,REASONCODE,REASONDESCRIPTION");
    procedures.write(NEWLINE);
    immunizations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST");
    immunizations.write(NEWLINE);
    encounters
        .write("Id,START,STOP,PATIENT,PROVIDER,ENCOUNTERCLASS,CODE,DESCRIPTION,COST," + "REASONCODE,REASONDESCRIPTION");
    encounters.write(NEWLINE);
    imagingStudies.write("Id,DATE,PATIENT,ENCOUNTER,BODYSITE_CODE,BODYSITE_DESCRIPTION,"
        + "MODALITY_CODE,MODALITY_DESCRIPTION,SOP_CODE,SOP_DESCRIPTION");
    imagingStudies.write(NEWLINE);
    organizations.write("Id,NAME,ADDRESS,CITY,STATE,ZIP,LAT,LON,PHONE,UTILIZATION");
    organizations.write(NEWLINE);
    providers.write("Id,ORGANIZATION,NAME,GENDER,SPECIALITY,ADDRESS,CITY,STATE,ZIP,LAT,LON,UTILIZATION");
    providers.write(NEWLINE);
  }

  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final CSVExporter instance = new CSVExporter();
  }

  /**
   * Get the current instance of the CSVExporter.
   * 
   * @return the current instance of the CSVExporter.
   */
  public static CSVExporter getInstance() {
    return SingletonHolder.instance;
  }

  /**
   * Export the organizations.csv and providers.csv files. This method should be
   * called once after all the Patient records have been exported using the
   * export(Person,long) method.
   * 
   * @throws IOException if any IO errors occur.
   */
  public void exportOrganizationsAndProviders() throws IOException {
    for (Provider org : Provider.getProviderList()) {
      // Check utilization for hospital before we export
      Table<Integer, String, AtomicInteger> utilization = org.getUtilization();
      int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
      if (totalEncounters > 0) {
        organization(org, totalEncounters);
        Map<String, ArrayList<Clinician>> providers = org.clinicianMap;
        for (String speciality : providers.keySet()) {
          ArrayList<Clinician> clinicians = providers.get(speciality);
          for (Clinician clinician : clinicians) {
            provider(clinician, org.getResourceID());
          }
        }
      }
      organizations.flush();
      providers.flush();
    }
  }

  /**
   * Add a single Person's health record info to the CSV records.
   * 
   * @param person Person to write record data for
   * @param time   Time the simulation ended
   * @throws IOException if any IO error occurs
   */
  public void export(Person person, long time) throws IOException {
    String personID = patient(person, time, ++pcounter);

    for (Encounter encounter : person.record.encounters) {
      String encounterID = encounter(personID, encounter, ++ecounter);

      for (HealthRecord.Entry condition : encounter.conditions) {
        condition(personID, encounterID, condition);
      }

      for (HealthRecord.Entry allergy : encounter.allergies) {
        allergy(personID, encounterID, allergy);
      }

      for (Observation observation : encounter.observations) {
        observation(personID, encounterID, observation);
      }

      for (Procedure procedure : encounter.procedures) {
        procedure(personID, encounterID, procedure);
      }

      for (Medication medication : encounter.medications) {
        medication(personID, encounterID, medication, time);
      }

      for (HealthRecord.Entry immunization : encounter.immunizations) {
        immunization(personID, encounterID, immunization);
      }

      for (CarePlan careplan : encounter.careplans) {
        careplan(personID, encounterID, careplan);
      }

      for (ImagingStudy imagingStudy : encounter.imagingStudies) {
        imagingStudy(personID, encounterID, imagingStudy);
      }
    }

    patients.flush();
    encounters.flush();
    conditions.flush();
    allergies.flush();
    medications.flush();
    careplans.flush();
    observations.flush();
    procedures.flush();
    immunizations.flush();
    imagingStudies.flush();
  }

  /**
   * Write a single Patient line, to patients.csv.
   *
   * @param person Person to write data for
   * @param time   Time the simulation ended, to calculate age/deceased status
   * @return the patient's ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  /*
   * private String patient(Person person, long time) throws IOException { //
   * Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX, //
   * FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
   * String personID = (String) person.attributes.get(Person.ID);
   * 
   * // check if we've already exported this patient demographic data yet, //
   * otherwise the "split record" feature could add a duplicate entry. if
   * (person.attributes.containsKey("exported_to_csv")) { return personID; } else
   * { person.attributes.put("exported_to_csv", personID); }
   * 
   * StringBuilder s = new StringBuilder(); s.append(personID).append(',');
   * s.append(dateFromTimestamp((long)person.attributes.get(Person.BIRTHDATE))).
   * append(','); if (!person.alive(time)) {
   * s.append(dateFromTimestamp(person.events.event(Event.DEATH).time)); }
   * 
   * for (String attribute : new String[] { Person.IDENTIFIER_SSN,
   * Person.IDENTIFIER_DRIVERS, Person.IDENTIFIER_PASSPORT, Person.NAME_PREFIX,
   * Person.FIRST_NAME, Person.LAST_NAME, Person.NAME_SUFFIX, Person.MAIDEN_NAME,
   * Person.MARITAL_STATUS, Person.RACE, Person.ETHNICITY, Person.GENDER,
   * Person.BIRTHPLACE, Person.ADDRESS, Person.CITY, Person.STATE, "county",
   * Person.ZIP }) { String value = (String)
   * person.attributes.getOrDefault(attribute, "");
   * s.append(',').append(clean(value)); }
   * 
   * s.append(',').append(person.getY()).append(',').append(person.getX());
   * 
   * s.append(NEWLINE); write(s.toString(), patients);
   * 
   * return personID; }
   */
  
  public String addData(String val) {
    String s = "\""+val+"\",";
    if(val == null)
      s = ",";
    return s;
  } 

  public String colSkip(int n) {
    String s = "";
    for (int i = 0; i < n; i++)
      s += ",";
    return s;
  }

  public String familysize() {
    int size;

    Random ran = new Random();

    int nxt = 1 + ran.nextInt(99);

    if (nxt < 28)
      size = 1;
    else if (nxt < 63)
      size = 2;
    else if (nxt < 78)
      size = 3;
    else if (nxt < 91)
      size = 4;
    else if (nxt < 97)
      size = 5;
    else if (nxt < 99)
      size = 6;
    else
      size = 7;

    return "" + size;
  }

  public String sdfTime(Date miliTime) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return (String) sdf.format(miliTime);
  }

  public String randomTimeGen(int startYear, int age) {
    // System.out.println("Start Year: "+ startYear);
    // System.out.println("Age: "+ age);
    
    Random rand = new Random();

    int year = Math.abs(startYear) + rand.nextInt(Math.abs(age)); // generate a year between start and
                                                                                  // end;
    int dayOfYear = 1 + rand.nextInt(Math.abs(364)); // generate a number between 1 and 365 (or 366 if you need to handle leap
                                           // year);
    int hours = 9 + rand.nextInt(12); // Clinic open from 9 AM to 11 PM
    int mins = rand.nextInt(59);
    int secs = rand.nextInt(59);

    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
    calendar.set(Calendar.HOUR, hours);
    calendar.set(Calendar.MINUTE, mins);
    calendar.set(Calendar.SECOND, secs);

    // System.out.println((String)sdf.format(calendar.getTime()));
    return sdfTime(calendar.getTime());
  }

  private String patient(Person person, long time, long id) throws IOException {
    // Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,
    // FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
    // String personID = (String) person.attributes.get(Person.ID);

    String personID = String.valueOf(id);
    person.attributes.put(Person.ID, personID);

    // check if we've already exported this patient demographic data yet,
    // otherwise the "split record" feature could add a duplicate entry.
    if (person.attributes.containsKey("exported_to_csv")) {
      return personID;
    } else {
      person.attributes.put("exported_to_csv", personID);
    }

    StringBuilder s = new StringBuilder();
    s.append(addData(personID));
    String val = (String) person.attributes.getOrDefault(Person.NAME_PREFIX, "");
    s.append(addData(clean(val))).append(colSkip(2));
    String totalName = "";
    for (String attribute : new String[] { Person.FIRST_NAME, Person.LAST_NAME, }) {
      String value = (String) person.attributes.get(attribute);
      totalName += value;
      s.append(addData(clean(value)));
    }
    s.append(colSkip(1));

    // Calculate age from birthday timestamp
    long birthtime = (long) person.attributes.get(Person.BIRTHDATE);
    long currenttime = System.currentTimeMillis();
    // System.out.println("Birthday Time: "+ birthtime);
    // System.out.println("Current Time: "+ currenttime);

    // Calendar c1 = Calendar.getInstance();
    Calendar c2 = Calendar.getInstance();
    // //Set time in milliseconds
    // c1.setTimeInMillis(currenttime);
    c2.setTimeInMillis(birthtime);

    // int age = c1.get(Calendar.YEAR) - c2.get(Calendar.YEAR);

    // System.out.println("Age: " + person.ageInYears(time));
    String dateOfVisit;
    if(person.ageInYears(time) != 0)
      dateOfVisit = randomTimeGen(c2.get(Calendar.YEAR), person.ageInYears(time));
    else
      dateOfVisit = sdfTime(new Date(System.currentTimeMillis()));
    // System.out.println("Time of Hospital Entry: " + dateOfVisit);
    // start year is the year of birth
    // end year is year of birth + age

    s.append(addData(clean(dateFromTimestamp(birthtime))));
    // if (!person.alive(time)) {
    // s.append(dateFromTimestamp(person.events.event(Event.DEATH).time));
    // }

    for (String attribute : new String[] { Person.ADDRESS, Person.ZIP, Person.CITY, Person.STATE, }) {
      String value = (String) person.attributes.get(attribute);
      s.append(addData(clean(value)));
    }

    s.append(colSkip(1));

    // for (String attribute : new String[] {
    // Person.IDENTIFIER_SSN,
    // Person.OCCUPATION_LEVEL,
    // }) {
    // String value = (String) person.attributes.getOrDefault(attribute, "");
    // s.append(clean(value)).append(',');
    // }
    val = (String) person.attributes.get(Person.IDENTIFIER_DRIVERS);
    s.append(addData(clean(val)));
    val = (String) person.attributes.get(Person.IDENTIFIER_SSN);
    s.append(addData(clean(val)));
    // Adding occupation

    String[] occupations = { "sales", "cashier", "clerk", "nurse", "waiter", "mover", "janitor", "secretary",
        "accounting", "manager", "truck driver", "teacher" };
    Random r = new Random();
    int randomNumber = r.nextInt(occupations.length);

    s.append(addData(clean(occupations[randomNumber]))).append(colSkip(2));

    val = (String) person.attributes.get(Person.TELECOM);
    s.append(addData(clean(val))).append(colSkip(2));

    val = (String) person.attributes.get(Person.MARITAL_STATUS);
    s.append(addData(clean(val))).append(colSkip(1));

    s.append(addData(dateOfVisit));
    val = (String) person.attributes.get(Person.GENDER);
    s.append(addData(clean(val)));
    /*
     * Person.NAME_SUFFIX, Person.MAIDEN_NAME, Person.MARITAL_STATUS, Person.RACE,
     * Person.ETHNICITY, Person.GENDER, Person.BIRTHPLACE,
     * 
     * "county",
     * 
     * 
     */
    String deathTime = "";
    String deathReason = "";
    if (person.record.death != null) {
      // System.out.print("Death Day: ");
      Date d = new Date(person.record.death); // * 1000

      deathTime = sdfTime(d);

      if (person.attributes.get(Person.CAUSE_OF_DEATH) != null) {
        Code c = (Code) person.attributes.get(Person.CAUSE_OF_DEATH);
        if (c.display != null)
          deathReason = (String) c.display;
        else if (c.code != null)
          deathReason = (String) c.code;
      }
      else
          deathReason = "No reason";
    }

    s.append(colSkip(4));

    String email = totalName + "@email.com";
    // System.out.println("Email ID: " + email);
    s.append(addData(email) + colSkip(2));

    for (String attribute : new String[] { Person.RACE, Person.ETHNICITY, }) {
      String value = "" + person.attributes.get(attribute);
      s.append(addData(clean(value)));
    }

    s.append(colSkip(3));

    // Family Size
    s.append(addData(familysize()));

    val = "" + person.attributes.get(Person.INCOME);
    s.append(addData(clean(val)));
    s.append(colSkip(4));

    s.append(addData(personID));

    s.append(colSkip(40));

    // Deceased Date and Reason
    s.append(deathTime + ",");

    s.append(addData(deathReason));

    s.append(colSkip(3));

    val = (String) person.attributes.get("county");
    s.append(addData(clean(val)));

    s.append(colSkip(16));

    // s.append(person.getY()).append(',').append(person.getX());

    s.append(NEWLINE);
    write(s.toString(), patients);

    return personID;
  }

  /**
   * Write a single Encounter line to encounters.csv.
   *
   * @param personID  The ID of the person that had this encounter
   * @param encounter The encounter itself
   * @return The encounter ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private String encounter(String personID, Encounter encounter, long eID) throws IOException {
    // Id,START,STOP,PATIENT,PROVIDER,ENCOUNTERCLASS,CODE,DESCRIPTION,COST,
    // REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    // String encounterID = UUID.randomUUID().toString();
    String encounterID = "" + eID;
    // ID
    s.append(addData(encounterID));
    // START
    s.append(addData(iso8601Timestamp(encounter.start)));
    // STOP
    if (encounter.stop != 0L) {
      s.append(addData(iso8601Timestamp(encounter.stop)));
    } else {
      s.append(colSkip(1));
    }
    // PATIENT
    s.append(addData(personID));

    // PROVIDER
    if (encounter.provider != null) {
      s.append(addData(encounter.provider.getResourceID()));
    } else {
      s.append(colSkip(1));
    }

    // ENCOUNTERCLASS
    if (encounter.type != null) {
      s.append(addData(encounter.type.toLowerCase()));
    } else {
      s.append(colSkip(1));
    }
    // CODE
    Code coding = encounter.codes.get(0);
    s.append(addData(coding.code));
    // DESCRIPTION
    s.append(addData(clean(coding.display)));
    // COST
    s.append(addData(String.format(Locale.US, "%.2f", encounter.cost())));
    // REASONCODE & REASONDESCRIPTION
    if (encounter.reason == null) {
      s.append(colSkip(1));
    } else {
      s.append(addData(encounter.reason.code));
      s.append("\""+clean(encounter.reason.display)+"\"");
    }

    s.append(NEWLINE);
    write(s.toString(), encounters);

    return encounterID;
  }

  /**
   * Write a single Condition to conditions.csv.
   *
   * @param personID    ID of the person that has the condition.
   * @param encounterID ID of the encounter where the condition was diagnosed
   * @param condition   The condition itself
   * @throws IOException if any IO error occurs
   */
  private void condition(String personID, String encounterID, Entry condition) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(condition.start)).append(',');
    if (condition.stop != 0L) {
      s.append(dateFromTimestamp(condition.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = condition.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display));

    s.append(NEWLINE);
    write(s.toString(), conditions);
  }

  /**
   * Write a single Allergy to allergies.csv.
   *
   * @param personID    ID of the person that has the allergy.
   * @param encounterID ID of the encounter where the allergy was diagnosed
   * @param allergy     The allergy itself
   * @throws IOException if any IO error occurs
   */
  private void allergy(String personID, String encounterID, Entry allergy) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(allergy.start)).append(',');
    if (allergy.stop != 0L) {
      s.append(dateFromTimestamp(allergy.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = allergy.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display));

    s.append(NEWLINE);
    write(s.toString(), allergies);
  }

  /**
   * Write a single Observation to observations.csv.
   *
   * @param personID    ID of the person to whom the observation applies.
   * @param encounterID ID of the encounter where the observation was taken
   * @param observation The observation itself
   * @throws IOException if any IO error occurs
   */
  private void observation(String personID, String encounterID, Observation observation) throws IOException {

    if (observation.value == null) {
      if (observation.observations != null && !observation.observations.isEmpty()) {
        // just loop through the child observations

        for (Observation subObs : observation.observations) {
          observation(personID, encounterID, subObs);
        }
      }

      // no value so nothing more to report here
      return;
    }

    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(observation.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = observation.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    String value = ExportHelper.getObservationValue(observation);
    String type = ExportHelper.getObservationType(observation);
    s.append(value).append(',');
    s.append(observation.unit).append(',');
    s.append(type);

    s.append(NEWLINE);
    write(s.toString(), observations);
  }

  /**
   * Write a single Procedure to procedures.csv.
   *
   * @param personID    ID of the person on whom the procedure was performed.
   * @param encounterID ID of the encounter where the procedure was performed
   * @param procedure   The procedure itself
   * @throws IOException if any IO error occurs
   */
  private void procedure(String personID, String encounterID, Procedure procedure) throws IOException {
    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(procedure.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = procedure.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    s.append(String.format(Locale.US, "%.2f", procedure.cost())).append(',');

    if (procedure.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
      Code reason = procedure.reasons.get(0);
      s.append(reason.code).append(',');
      s.append(clean(reason.display));
    }

    s.append(NEWLINE);
    write(s.toString(), procedures);
  }

  /**
   * Write a single Medication to medications.csv.
   *
   * @param personID    ID of the person prescribed the medication.
   * @param encounterID ID of the encounter where the medication was prescribed
   * @param medication  The medication itself
   * @param stopTime    End time
   * @throws IOException if any IO error occurs
   */
  private void medication(String personID, String encounterID, Medication medication, long stopTime)
      throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,
    // COST,DISPENSES,TOTALCOST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(medication.start)).append(',');
    if (medication.stop != 0L) {
      s.append(dateFromTimestamp(medication.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = medication.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    BigDecimal cost = medication.cost();
    s.append(String.format(Locale.US, "%.2f", cost)).append(',');
    long dispenses = 1; // dispenses = refills + original
    // makes the math cleaner and more explicit. dispenses * unit cost = total cost

    long stop = medication.stop;
    if (stop == 0L) {
      stop = stopTime;
    }
    long medDuration = stop - medication.start;

    if (medication.prescriptionDetails != null && medication.prescriptionDetails.has("refills")) {
      dispenses = medication.prescriptionDetails.get("refills").getAsInt();
    } else if (medication.prescriptionDetails != null && medication.prescriptionDetails.has("duration")) {
      JsonObject duration = medication.prescriptionDetails.getAsJsonObject("duration");

      long quantity = duration.get("quantity").getAsLong();
      String unit = duration.get("unit").getAsString();
      long durationMs = Utilities.convertTime(unit, quantity);
      dispenses = medDuration / durationMs;
    } else {
      // assume 1 refill / month
      long durationMs = Utilities.convertTime("months", 1);
      dispenses = medDuration / durationMs;
    }

    if (dispenses < 1) {
      // integer division could leave us with 0,
      // ex. if the active time (start->stop) is less than the provided duration
      // or less than a month if no duration provided
      dispenses = 1;
    }

    s.append(dispenses).append(',');
    BigDecimal totalCost = cost.multiply(BigDecimal.valueOf(dispenses)).setScale(2, RoundingMode.DOWN); // truncate to 2
                                                                                                        // decimal
                                                                                                        // places
    s.append(String.format(Locale.US, "%.2f", totalCost)).append(',');

    if (medication.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
      Code reason = medication.reasons.get(0);
      s.append(reason.code).append(',');
      s.append(clean(reason.display));
    }

    s.append(NEWLINE);
    write(s.toString(), medications);
  }

  /**
   * Write a single Immunization to immunizations.csv.
   *
   * @param personID     ID of the person on whom the immunization was performed.
   * @param encounterID  ID of the encounter where the immunization was performed
   * @param immunization The immunization itself
   * @throws IOException if any IO error occurs
   */
  private void immunization(String personID, String encounterID, Entry immunization) throws IOException {
    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(immunization.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = immunization.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    s.append(String.format(Locale.US, "%.2f", immunization.cost()));

    s.append(NEWLINE);
    write(s.toString(), immunizations);
  }

  /**
   * Write a single CarePlan to careplans.csv.
   *
   * @param personID    ID of the person prescribed the careplan.
   * @param encounterID ID of the encounter where the careplan was prescribed
   * @param careplan    The careplan itself
   * @throws IOException if any IO error occurs
   */
  private String careplan(String personID, String encounterID, CarePlan careplan) throws IOException {
    // Id,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    String careplanID = UUID.randomUUID().toString();
    s.append(careplanID).append(',');
    s.append(dateFromTimestamp(careplan.start)).append(',');
    if (careplan.stop != 0L) {
      s.append(dateFromTimestamp(careplan.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = careplan.codes.get(0);

    s.append(coding.code).append(',');
    s.append(coding.display).append(',');

    if (careplan.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
      Code reason = careplan.reasons.get(0);
      s.append(reason.code).append(',');
      s.append(clean(reason.display));
    }
    s.append(NEWLINE);

    write(s.toString(), careplans);

    return careplanID;
  }

  /**
   * Write a single ImagingStudy to imaging_studies.csv.
   *
   * @param personID     ID of the person the ImagingStudy was taken of.
   * @param encounterID  ID of the encounter where the ImagingStudy was performed
   * @param imagingStudy The ImagingStudy itself
   * @throws IOException if any IO error occurs
   */
  private String imagingStudy(String personID, String encounterID, ImagingStudy imagingStudy) throws IOException {
    // Id,DATE,PATIENT,ENCOUNTER,BODYSITE_CODE,BODYSITE_DESCRIPTION,
    // MODALITY_CODE,MODALITY_DESCRIPTION,SOP_CODE,SOP_DESCRIPTION
    StringBuilder s = new StringBuilder();

    String studyID = UUID.randomUUID().toString();
    s.append(studyID).append(',');
    s.append(dateFromTimestamp(imagingStudy.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    ImagingStudy.Series series1 = imagingStudy.series.get(0);
    ImagingStudy.Instance instance1 = series1.instances.get(0);

    Code bodySite = series1.bodySite;
    Code modality = series1.modality;
    Code sopClass = instance1.sopClass;

    s.append(bodySite.code).append(',');
    s.append(bodySite.display).append(',');

    s.append(modality.code).append(',');
    s.append(modality.display).append(',');

    s.append(sopClass.code).append(',');
    s.append(sopClass.display);

    s.append(NEWLINE);

    write(s.toString(), imagingStudies);

    return studyID;
  }

  /**
   * Write a single organization to organizations.csv
   * 
   * @param org         The organization to be written
   * @param utilization The total number of encounters for the org
   * @throws IOException if any IO error occurs
   */
  private void organization(Provider org, int utilization) throws IOException {
    // Id,NAME,ADDRESS,CITY,STATE,ZIP,PHONE,UTILIZATION
    StringBuilder s = new StringBuilder();
    s.append(org.getResourceID()).append(',');
    s.append(clean(org.name)).append(',');
    s.append(clean(org.address)).append(',');
    s.append(org.city).append(',');
    s.append(org.state).append(',');
    s.append(org.zip).append(',');
    s.append(org.getY()).append(',');
    s.append(org.getX()).append(',');
    s.append(org.phone).append(',');
    s.append(utilization);
    s.append(NEWLINE);

    write(s.toString(), organizations);
  }

  /**
   * Write a single clinician to providers.csv
   * 
   * @param provider The provider information to be written
   * @param orgId    ID of the organization the provider belongs to
   * @throws IOException if any IO error occurs
   */
  private void provider(Clinician provider, String orgId) throws IOException {
    // Id,ORGANIZATION,NAME,GENDER,SPECIALITY,ADDRESS,CITY,STATE,ZIP,UTILIZATION

    StringBuilder s = new StringBuilder();
    s.append(provider.getResourceID()).append(',');
    s.append(orgId).append(',');
    for (String attribute : new String[] { Clinician.NAME, Clinician.GENDER, Clinician.SPECIALTY, Clinician.ADDRESS,
        Clinician.CITY, Clinician.STATE, Clinician.ZIP }) {
      String value = (String) provider.attributes.getOrDefault(attribute, "");
      s.append(clean(value)).append(',');
    }
    s.append(provider.getY()).append(',');
    s.append(provider.getX()).append(',');
    s.append(provider.getEncounterCount());

    s.append(NEWLINE);

    write(s.toString(), providers);

  }

  /**
   * Replaces commas and line breaks in the source string with a single space.
   * Null is replaced with the empty string.
   */
  private static String clean(String src) {
    if (src == null) {
      return "";
    } else {
      return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
    }
  }

  /**
   * Helper method to write a line to a File. Extracted to a separate method here
   * to make it a little easier to replace implementations.
   *
   * @param line   The line to write
   * @param writer The place to write it
   * @throws IOException if an I/O error occurs
   */
  private static void write(String line, FileWriter writer) throws IOException {
    synchronized (writer) {
      writer.write(line);
    }
  }
}
