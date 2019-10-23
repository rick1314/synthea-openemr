package org.mitre.synthea.modules;

import static org.mitre.synthea.modules.LifecycleModule.bmi;
import static org.mitre.synthea.modules.LifecycleModule.lookupGrowthChart;
import static org.mitre.synthea.modules.LifecycleModule.percentileForBMI;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.BiometricsConfig;
import org.mitre.synthea.world.concepts.VitalSign;

/**
 * This module allows patients in Synthea to lose weight. It will be triggered when patients
 * hit a specified age and weight threshold. At that point, patients may chose to manage
 * their weight. If they do, it will suspend any weight adjustments from the LifecycleModule.
 * Patients will then lose weight based on adherence generated by this module. Given successful
 * weight loss, the patients have the chance to later regain all of their weight (very likely
 * given the default probabilities).
 */
public final class WeightLossModule extends Module {

  public WeightLossModule() {
    this.name = "Weight Loss";
  }

  public static final String ACTIVE_WEIGHT_MANAGEMENT = "active_weight_management";
  public static final String PRE_MANAGEMENT_WEIGHT = "pre_management_weight";
  public static final String WEIGHT_MANAGEMENT_START = "weight_management_start";
  public static final String WEIGHT_LOSS_PERCENTAGE = "weight_loss_percentage";
  public static final String WEIGHT_LOSS_BMI_PERCENTILE_CHANGE
      = "weight_loss_bmi_percentile_change";
  public static final String LONG_TERM_WEIGHT_LOSS = "long_term_weight_loss";
  public static final String WEIGHT_LOSS_ADHERENCE = "weight_loss_adherence";

  public static final int managementStartAge = (int) BiometricsConfig.get("min_age", 5);
  public static final double startWeightManagementProb =
      (double) BiometricsConfig.get("start_prob", 0.493);
  public static final double adherence =
      (double) BiometricsConfig.get("adherence", 0.605);
  public static final double startBMI =
      (double) BiometricsConfig.get("start_bmi", 30d);
  public static final double startPercentile =
      (double) BiometricsConfig.get("start_percentile", 0.85d);
  public static final double minLoss = (double) BiometricsConfig.get("min_loss", 0.07);
  public static final double maxLoss = (double) BiometricsConfig.get("max_loss", 0.1);
  public static final double maintenance = (double) BiometricsConfig.get("maintenance", 0.2);
  public static final double maxPedPercentileChange =
      (double) BiometricsConfig.get("max_ped_percentile_change", 0.1);


  @Override
  public boolean process(Person person, long time) {
    Object activeWeightManagement = person.attributes.get(ACTIVE_WEIGHT_MANAGEMENT);

    // First check to see if they are under active weight management
    if (activeWeightManagement != null && (boolean) activeWeightManagement) {
      boolean longTermSuccess = (boolean) person.attributes.get(LONG_TERM_WEIGHT_LOSS);
      int age = person.ageInYears(time);
      int ageInMonths = person.ageInMonths(time);
      String gender = (String) person.attributes.get(Person.GENDER);
      // In the first year of management, if there is adherence, the person will lose
      // weight
      if (firstYearOfManagement(person, time)) {
        manageFirstYearWeight(person, time);
      } else if (firstFiveYearsOfManagement(person, time)) {
        manageYearTwoThroughFive(person, time);
      } else {
        // five years after the start
        if (longTermSuccess) {
          if (age < 20) {
            // The person will continue to grow, increase their weight, but keep BMI steady
            double height = lookupGrowthChart("height", gender, ageInMonths,
                person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));
            double weight = maintainBMIPercentile(person, time);
            person.setVitalSign(VitalSign.WEIGHT, weight);
            person.setVitalSign(VitalSign.HEIGHT, height);
            person.setVitalSign(VitalSign.BMI, bmi(height, weight));
          }
        } else {
          stopWeightManagement(person);
        }
      }
    } else {
      boolean willStart = willStartWeightManagement(person, time);
      if (willStart) {
        startWeightManagement(person, time);
      }
    }
    return false;
  }

  /**
   * This method handles all weight management cases (adherent and non-adherent) for the first year
   * of weight management.
   */
  public void manageFirstYearWeight(Person person, long time) {
    boolean followsPlan = (boolean) person.attributes.get(WEIGHT_LOSS_ADHERENCE);
    int age = person.ageInYears(time);
    int ageInMonths = person.ageInMonths(time);
    String gender = (String) person.attributes.get(Person.GENDER);
    double weight;
    if (followsPlan) {
      if (age < 20) {
        weight = pediatricWeightLoss(person, time);
      } else {
        weight = adultWeightLoss(person, time);
      }
    } else {
      if (age < 20) {
        // Not following the plan. Keep along the weight percentile per the growth chart
        weight = lookupGrowthChart("weight", gender, ageInMonths,
            person.getVitalSign(VitalSign.WEIGHT_PERCENTILE, time));
      } else {
        // Not following the plan. Just keep the weight steady
        weight = person.getVitalSign(VitalSign.WEIGHT, time);
      }
    }
    double height = person.getVitalSign(VitalSign.HEIGHT, time);
    person.setVitalSign(VitalSign.WEIGHT, weight);
    person.setVitalSign(VitalSign.BMI, bmi(height, weight));
  }

  /**
   * This method handles all weight management cases (adherent and non-adherent) for the year two
   * through year five of weight management.
   */
  public void manageYearTwoThroughFive(Person person, long time) {
    boolean followsPlan = (boolean) person.attributes.get(WEIGHT_LOSS_ADHERENCE);
    boolean longTermSuccess = (boolean) person.attributes.get(LONG_TERM_WEIGHT_LOSS);
    int age = person.ageInYears(time);
    int ageInMonths = person.ageInMonths(time);
    String gender = (String) person.attributes.get(Person.GENDER);
    // In the next 5 years, if someone has lost weight, check to see if they
    // will have long term success. If they don't, revert their weight back
    // to the original weight
    if (followsPlan) {
      if (longTermSuccess) {
        if (age < 20) {
          // The person will continue to grow, increase their weight, but keep BMI steady
          double height = lookupGrowthChart("height", gender, ageInMonths,
              person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));
          double weight = maintainBMIPercentile(person, time);
          person.setVitalSign(VitalSign.WEIGHT, weight);
          person.setVitalSign(VitalSign.HEIGHT, height);
          person.setVitalSign(VitalSign.BMI, bmi(height, weight));
        }
      } else {
        double weight;
        double height;
        if (age < 20) {
          height = lookupGrowthChart("height", gender, ageInMonths,
              person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));

          weight = pediatricRegression(person, time);
        } else {
          long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
          if (person.ageInYears(start) < 20) {
            if (age >= 20) {
              height = lookupGrowthChart("height", gender, 240,
                  person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));
            } else {
              height = lookupGrowthChart("height", gender, ageInMonths,
                  person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));
            }

            weight = transitionRegression(person, time);
          } else {
            height = person.getVitalSign(VitalSign.HEIGHT, time);
            weight = adultRegression(person, time);
          }
        }

        person.setVitalSign(VitalSign.HEIGHT, height);
        person.setVitalSign(VitalSign.WEIGHT, weight);
        person.setVitalSign(VitalSign.BMI, bmi(height, weight));
      }
    } else {
      if (age < 20) {
        // Didn't follow the plan. Keep along the weight percentile per the growth chart
        double weight = lookupGrowthChart("weight", gender, ageInMonths,
            person.getVitalSign(VitalSign.WEIGHT_PERCENTILE, time));
        double height = lookupGrowthChart("height", gender, ageInMonths,
            person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));
        person.setVitalSign(VitalSign.HEIGHT, height);
        person.setVitalSign(VitalSign.WEIGHT, weight);
        person.setVitalSign(VitalSign.BMI, bmi(height, weight));
      }
    }
  }

  /**
   * Person stops weight management. The module will remove all weight management related
   * attributes.
   */
  public void stopWeightManagement(Person person) {
    person.attributes.remove(WEIGHT_MANAGEMENT_START);
    person.attributes.remove(WEIGHT_LOSS_PERCENTAGE);
    person.attributes.remove(WEIGHT_LOSS_ADHERENCE);
    person.attributes.remove(WEIGHT_LOSS_BMI_PERCENTILE_CHANGE);
    person.attributes.remove(PRE_MANAGEMENT_WEIGHT);
    person.attributes.remove(LONG_TERM_WEIGHT_LOSS);
    person.attributes.put(ACTIVE_WEIGHT_MANAGEMENT, false);
  }

  /**
   * Determines whether the person is currently within their first year of active weight management
   * based on the WEIGHT_MANAGEMENT_START attribute.
   */
  public boolean firstYearOfManagement(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    return start >= time - Utilities.convertTime("years", 1);
  }

  /**
   * Determines whether the person is currently within their first five years of active weight
   * management based on the WEIGHT_MANAGEMENT_START attribute.
   */
  public boolean firstFiveYearsOfManagement(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    return start >= time - Utilities.convertTime("years", 5);
  }

  /**
   * Weight loss is linear from the person's start weight to their target
   * weight (start - percentage loss) over the first year of active weight management.
   * Returns the new weight for the person.
   */
  public double adultWeightLoss(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    double year = Utilities.convertTime("years", 1);
    double percentOfYearElapsed = (time - start) / year;
    double startWeight = (double) person.attributes.get(PRE_MANAGEMENT_WEIGHT);
    double lossPercent = (double) person.attributes.get(WEIGHT_LOSS_PERCENTAGE);
    return startWeight - (startWeight * lossPercent * percentOfYearElapsed);
  }

  /**
   * Weight regression is linear from a person's current weight to their original weight over the
   * second through fifth year of active weight management. Returns the new weight for the person.
   */
  public double adultRegression(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    double percentOfTimeElapsed = (time - start - Utilities.convertTime("years", 1))
        / (double) Utilities.convertTime("years", 4);
    double startWeight = (double) person.attributes.get(PRE_MANAGEMENT_WEIGHT);
    double lossPercent = (double) person.attributes.get(WEIGHT_LOSS_PERCENTAGE);
    double minWeight = startWeight - (startWeight * lossPercent);
    return startWeight - ((startWeight - minWeight) * (1 - percentOfTimeElapsed));
  }

  /**
   * This will regress a pediatric patient back to their weight percentile. Weight gain will not
   * necessarily be linear. It will approach the weight based on percentile at age as a function
   * of time in the regression period.
   */
  public double pediatricRegression(Person person, long time) {
    return percentileRegression(person, time);
  }

  /**
   * Revert the person to their 240 month weight percentile following the same procedure as
   * pediatric regression.
   */
  public double transitionRegression(Person person, long time) {
    return percentileRegression(person, time);
  }

  private double percentileRegression(Person person, long time) {
    String gender = (String) person.attributes.get(Person.GENDER);
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    int startAgeInMonths = person.ageInMonths(start);
    double assignedWeightPercentile = person.getVitalSign(VitalSign.WEIGHT_PERCENTILE, time);
    double assignedHeightPercentile = person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time);
    double percentileChange = (double) person.attributes.get(WEIGHT_LOSS_BMI_PERCENTILE_CHANGE);
    double lowestBMI = calculateBMIAfterSuccessfulFirstYear(percentileChange, gender,
        startAgeInMonths, assignedWeightPercentile, assignedHeightPercentile);
    int regressionEndAgeInMonths = startAgeInMonths + 60;
    if (regressionEndAgeInMonths > 240) {
      regressionEndAgeInMonths = 240; // Fixing the end age at the end of the growth charts.
      // We only need this to find the final BMI to move towards.
    }
    double endWeight = lookupGrowthChart("weight", gender,
        regressionEndAgeInMonths, assignedWeightPercentile);
    double endHeight = lookupGrowthChart("height", gender,
        regressionEndAgeInMonths, assignedHeightPercentile);
    double endBMI = bmi(endHeight, endWeight);
    double percentOfTimeElapsed = (time - start - Utilities.convertTime("years", 1))
        / (double) Utilities.convertTime("years", 4);
    double currentBMI = endBMI - ((endBMI - lowestBMI) * (1 - percentOfTimeElapsed));
    int currentAgeInMonths = person.ageInMonths(time);
    if (currentAgeInMonths > 240) {
      currentAgeInMonths = 240; // Same as above
    }
    double currentHeight = lookupGrowthChart("height", gender, currentAgeInMonths,
        assignedHeightPercentile);
    return currentBMI * (currentHeight / 100.0) * (currentHeight / 100.0);
  }

  /**
   * Patients under 20 will lose/manage weight as a change to their BMI percentile. This change will
   * be somewhere between 0 and max_ped_bmi_percentile_change as specified in biometrics.yml
   * (0.1 or 10 percentiles be default).
   *
   * <p>This function calculates the weight by taking WEIGHT_LOSS_BMI_CHANGE attribute and using a
   * percentage of that based on the amount of year that has elapsed since WEIGHT_MANAGEMENT_START.
   * It will determine the patient's BMI at the start of weight management and use that to determine
   * the patient's BMI start percentile. It will then subtract the percentile change and determine
   * what the patient's BMI will be at the end of the first year based on the new percentile.
   * Given the start and end BMI, the function will apply an appropriate portion of BMI change based
   * on the portion of the year that has elapsed. It will then calculate the weight by reversing
   * the BMI calculation with a height based on the patient's height in months for the time passed
   * in.</p>
   */
  public double pediatricWeightLoss(Person person, long time) {
    String gender = (String) person.attributes.get(Person.GENDER);
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    int startAgeInMonths = person.ageInMonths(start);
    double assignedWeightPercentile = person.getVitalSign(VitalSign.WEIGHT_PERCENTILE, time);
    double assignedHeightPercentile = person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time);
    double percentileChange = (double) person.attributes.get(WEIGHT_LOSS_BMI_PERCENTILE_CHANGE);
    double year = Utilities.convertTime("years", 1);
    double percentOfYearElapsed = (time - start) / year;
    double endBMI = calculateBMIAfterSuccessfulFirstYear(percentileChange, gender, startAgeInMonths,
        assignedWeightPercentile, assignedHeightPercentile);
    double startBMI = calculateStartBMI(gender, startAgeInMonths, assignedWeightPercentile,
        assignedHeightPercentile);
    double currentBMI = startBMI - ((startBMI - endBMI) * percentOfYearElapsed);
    int currentAgeInMonths = person.ageInMonths(time);
    double currentHeight = lookupGrowthChart("height", gender, currentAgeInMonths,
        assignedHeightPercentile);
    // weight = BMI * h^2
    double weight = currentBMI * (currentHeight / 100.0) * (currentHeight / 100.0);
    return weight;
  }

  /**
   * For patients under 20 that have successful weight management and long term success, they will
   * maintain their BMI until they reach age 20. This means that they will gain weight as they are
   * gaining height, but it will be in a more healthy range.
   */
  public double maintainBMIPercentile(Person person, long time) {
    String gender = (String) person.attributes.get(Person.GENDER);
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    int startAgeInMonths = person.ageInMonths(start);
    double assignedWeightPercentile = person.getVitalSign(VitalSign.WEIGHT_PERCENTILE, time);
    double assignedHeightPercentile = person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time);
    double percentileChange = (double) person.attributes.get(WEIGHT_LOSS_BMI_PERCENTILE_CHANGE);
    double bmiPercentile = calculateBMIPercentileAfterSuccessfulFirstYear(percentileChange, gender,
        startAgeInMonths, assignedWeightPercentile, assignedHeightPercentile);
    int ageInMonths = person.ageInMonths(time);
    double bmi = lookupGrowthChart("bmi", gender, ageInMonths, bmiPercentile);

    double currentHeight = lookupGrowthChart("height", gender, ageInMonths,
        assignedHeightPercentile);
    double weight = bmi * (currentHeight / 100.0) * (currentHeight / 100.0);
    return weight;
  }

  /**
   * Starts active weight management for the person. It will select if a person adheres to their
   * weight management plan. If they do, it will select the percentage of their body weight that
   * they will lose and whether they will keep it off long term.
   */
  public void startWeightManagement(Person person, long time) {
    double startWeight = person.getVitalSign(VitalSign.WEIGHT, time);
    person.attributes.put(ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(PRE_MANAGEMENT_WEIGHT, startWeight);
    person.attributes.put(WEIGHT_MANAGEMENT_START, time);
    boolean stickToPlan = person.rand() <= adherence;
    person.attributes.put(WEIGHT_LOSS_ADHERENCE, stickToPlan);
    if (stickToPlan) {
      if (person.ageInYears(time) >= 20) {
        double percentWeightLoss = person.rand(minLoss, maxLoss);
        person.attributes.put(WEIGHT_LOSS_PERCENTAGE, percentWeightLoss);
      } else {
        double bmiPercentileChange = person.rand() * maxPedPercentileChange;
        person.attributes.put(WEIGHT_LOSS_BMI_PERCENTILE_CHANGE, bmiPercentileChange);
      }
      boolean longTermSuccess = person.rand() <= maintenance;
      person.attributes.put(LONG_TERM_WEIGHT_LOSS, longTermSuccess);
    } else {
      person.attributes.put(LONG_TERM_WEIGHT_LOSS, false);
    }
  }

  /**
   * Determines whether a person will start weight management. If they meet the weight
   * management thresholds, there is a 49.3% chance that they will start
   * weight management. This does not mean that they will adhere to the management plan.
   */
  public boolean willStartWeightManagement(Person person, long time) {
    if (meetsWeightManagementThresholds(person, time)) {
      return person.rand() <= startWeightManagementProb;
    }
    return false;
  }

  /**
   * Determines whether a person meets the thresholds for starting weight management.
   * With the default settings:
   * Children under 5 do not ever meet the threshold.
   * Patients from ages 5 to 20 meet the threshold if their BMI is at or over the 85th percentile
   *   for their age in months
   * Patients 20 and older meet the threshold if their BMI is 30 or over.
   */
  public boolean meetsWeightManagementThresholds(Person person, long time) {
    int age = person.ageInYears(time);
    double bmi = person.getVitalSign(VitalSign.BMI, time);
    double bmiAtPercentile = 500; // initializing to an impossibly high value
    // if we somehow hit this later
    if (age >= 2 && age < 20) {
      int ageInMonths = person.ageInMonths(time);
      String gender = (String) person.attributes.get(Person.GENDER);
      bmiAtPercentile = lookupGrowthChart("bmi", gender,
          ageInMonths, startPercentile);
    }
    return (age >= managementStartAge && ((bmi >= startBMI && age >= 20)
        || (age < 20 && bmi >= bmiAtPercentile)));
  }

  private double calculateBMIPercentileAfterSuccessfulFirstYear(double percentileChange,
                                                                String gender,
                                                                int startAgeInMonths,
                                                                double assignedWeightPercentile,
                                                                double assignedHeightPercentile) {
    double startWeight = lookupGrowthChart("weight", gender, startAgeInMonths,
        assignedWeightPercentile);
    double startHeight = lookupGrowthChart("height", gender, startAgeInMonths,
        assignedHeightPercentile);
    double startBMI = bmi(startHeight, startWeight);
    double startBMIPercentile = percentileForBMI(startBMI, gender, startAgeInMonths);
    return startBMIPercentile - percentileChange;
  }

  private double calculateBMIAfterSuccessfulFirstYear(double percentileChange,
                                                      String gender,
                                                      int startAgeInMonths,
                                                      double assignedWeightPercentile,
                                                      double assignedHeightPercentile) {
    double endBMIPercentile = calculateBMIPercentileAfterSuccessfulFirstYear(percentileChange,
        gender, startAgeInMonths, assignedWeightPercentile, assignedHeightPercentile);
    return lookupGrowthChart("bmi", gender, startAgeInMonths + 12,
        endBMIPercentile);
  }

  private double calculateStartBMI(String gender,
                                   int startAgeInMonths,
                                   double assignedWeightPercentile,
                                   double assignedHeightPercentile) {
    double startWeight = lookupGrowthChart("weight", gender, startAgeInMonths,
        assignedWeightPercentile);
    double startHeight = lookupGrowthChart("height", gender, startAgeInMonths,
        assignedHeightPercentile);
    return bmi(startHeight, startWeight);
  }
}
