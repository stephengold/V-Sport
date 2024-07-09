#!/bin/bash

set -e

GitDir=~/NetBeansProjects
#GitDir="/c/Users/sgold/My Documents/NetBeansProjects"

S2="$GitDir/sport"
D2="$GitDir/V-Sport"

S3="$GitDir/LbjExamples/"
D3="$GitDir/V-Sport/"

S5="$GitDir/LbjExamples/apps/src/main/java/com/github/stephengold/lbjexamples"
D5="$GitDir/V-Sport/apps/src/main/java/com/github/stephengold/vsport/demo"

S6="$GitDir/LbjExamples/apps/src/main/java/com/github/stephengold/lbjexamples/apps"
D6="$GitDir/V-Sport/apps/src/main/java/com/github/stephengold/vsport/tutorial"

Meld="/usr/bin/meld"
#Meld="/c/Program Files/Meld/meld"

"$Meld" --diff "$S2" "$D2" --diff "$S3" "$D3" --diff "$S5" "$D5" --diff "$S6" "$D6"
