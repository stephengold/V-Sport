#!/bin/bash

set -e

GitDir=~/NetBeansProjects
#GitDir="/c/Users/sgold/My Documents/NetBeansProjects"

S1="$GitDir/sport"
D1="$GitDir/V-Sport"

S2="$GitDir/LbjExamples/"
D2="$GitDir/V-Sport/"

S3="$GitDir/LbjExamples/apps/src/main/java/com/github/stephengold/lbjexamples"
D3="$GitDir/V-Sport/apps/src/main/java/com/github/stephengold/vsport/demo"

S4="$GitDir/LbjExamples/apps/src/main/java/com/github/stephengold/lbjexamples/apps"
D4="$GitDir/V-Sport/apps/src/main/java/com/github/stephengold/vsport/tutorial"

S5="$GitDir/sport/lib/src/main/java/com/github/stephengold/sport"
D5="$GitDir/V-Sport/lib/src/main/java/com/github/stephengold/vsport"

Meld="/usr/bin/meld"
#Meld="/c/Program Files/Meld/meld"

"$Meld" --diff "$S1" "$D1" --diff "$S2" "$D2" --diff "$S3" "$D3" --diff "$S4" "$D4" --diff "$S5" "$D5"
