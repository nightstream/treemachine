#!/bin/bash
TEST_DIR=$(dirname $0)
HARNESS_DIR=$(dirname "$TEST_DIR")
source "$HARNESS_DIR"/synth-test-harness.sh
testsynthesis conflictingaugmenting 1 2

