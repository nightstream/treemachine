#!/bin/bash

clear

# these tests have been passed
# re-run them as a change has been made to the code
FAILING_TREEMACHINE_TEST=conflictingaugmenting ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=mapdeepestmctavish ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=preferresolved ./run_synth_tests.sh
FAILING_TREEMACHINE_TEST=overlapthroughtaxon ./run_synth_tests.sh

