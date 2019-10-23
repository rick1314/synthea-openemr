# Synthea<sup>TM</sup> Patient Generator 

Synthea<sup>TM</sup> is a Synthetic Patient Population Simulator. The goal is to output synthetic, realistic (but not real), patient data and associated health records in a variety of formats.

Read [wiki](https://github.com/synthetichealth/synthea/wiki) for more information.

Original [Repo](https://github.com/synthetichealth/synthea/


## Modifications 

This is a fork of synthea, I modified (src/main/java/org/mitre/synthea/export/CSVExporter.java) 
for generating patient data so that it can be used with [openemr] (https://www.open-emr.org/)

## Quick Start

### Installation

**System Requirements:**
Synthea<sup>TM</sup> requires Java 1.8 or above.

To clone the original Synthea<sup>TM</sup> repo, then build and run the test suite:
```
git clone https://github.com/synthetichealth/synthea.git
cd synthea
./gradlew build check test
```

To clone this fork of Synthea<sup>TM</sup> repo, then build and NOT run the test suite, as the tests won't pass due to some of the modifications I made:
```
git clone https://github.com/rick1314/synthea-openemr.git
cd synthea-openemr
./gradlew build -x test
```


### Generate Synthetic Patients
Generating the population one at a time...
```
./run_synthea
```

Command-line arguments may be provided to specify a state, city, population size, or seed for randomization.

Usage is 
```
run_synthea [-s seed] [-p populationSize] [-m moduleFilter] [state [city]]
```

All Options: 

[-s seed] [-cs clinicianSeed] [-p populationSize]
[-g gender] [-a minAge-maxAge]
[-o overflowPopulation]
[-m moduleFileWildcardList]
[-c localConfigFilePath]
[-d localModulesDirPath]
[--config* value]
 * any setting from src/main/resources/synthea.properties

For example:

 - `run_synthea Utah "Salt Lake City" -p 100` 
    
	This will generate data for 100 alive patients and some dead ones that live in Salt Lake City 
 
 - `run_synthea Massachusetts`
 - `run_synthea Alaska Juneau`
 - `run_synthea -s 12345`
 - `run_synthea -p 1000`
 - `run_synthea -s 987 Washington Seattle`
 - `run_synthea -s 21 -p 100 Utah "Salt Lake City"`
 - `run_synthea -m metabolic*`


Please modify `./src/main/resources/synthea.properties` as required.

Synthea<sup>TM</sup> will output patient records in C-CDA and FHIR formats in `./output`.

### For detailed poperties of patients 

Please read this [answer] (https://github.com/synthetichealth/synthea/issues/310)



### Synthea<sup>TM</sup> GraphViz
Generate graphical visualizations of Synthea<sup>TM</sup> rules and modules.
```
./gradlew graphviz
```

### Concepts and Attributes
Generate a list of concepts (used in the records) or attributes (variables on each patient).
```
./gradlew concepts
./gradlew attributes
```

# License

Copyright 2017-2019 The MITRE Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
