<img height="150" src="https://i.imgur.com/YEPFEcx.png" alt="Libbulletjme Project logo">

[V-Sport Project][project] will implement
a simple [Vulkan]-based graphics engine for physics demos.

It contains 2 sub-projects:

1. lib: the V-Sport graphics engine
2. apps: demos, tutorial examples, and non-automated test software

Complete source code (in [Java]) is provided under
[a BSD 3-Clause license][license].


## How to build and run V-Sport

### Initial build

1. Install a [Java Development Kit (JDK)][adoptium],
   if you don't already have one.
2. Point the `JAVA_HOME` environment variable to your JDK installation:
   (The path might be something like "C:\Program Files\Java\jre1.8.0_301"
   or "/usr/lib/jvm/java-8-openjdk-amd64/" or
   "/Library/Java/JavaVirtualMachines/liberica-jdk-17-full.jdk/Contents/Home" .)
  + using Bash or Zsh: `export JAVA_HOME="` *path to installation* `"`
  + using Windows Command Prompt: `set JAVA_HOME="` *path to installation* `"`
  + using PowerShell: `$env:JAVA_HOME = '` *path to installation* `'`
3. Download and extract the V-Sport source code from GitHub:
  + using Git:
    + `git clone https://github.com/stephengold/V-Sport.git`
    + `cd V-Sport`
4. Run the [Gradle] wrapper:
  + using Bash or PowerShell or Zsh: `./gradlew build`
  + using Windows Command Prompt: `.\gradlew build`

### Tutorials

The tutorial apps all have names starting with "Hello".
For instance, the first tutorial app is named "HelloVSport".

To execute "HelloVSport":
+ using Bash or PowerShell or Zsh: `./gradlew HelloVSport`
+ using Windows Command Prompt: `.\gradlew HelloVSport`

### Cleanup

You can restore the project to a pristine state:
+ using Bash or PowerShell or Zsh: `./gradlew clean`
+ using Windows Command Prompt: `.\gradlew clean`


[adoptium]: https://adoptium.net/releases.html "Adoptium Project"
[gradle]: https://gradle.org "Gradle Project"
[java]: https://en.wikipedia.org/wiki/Java_(programming_language) "Java programming language"
[license]: https://github.com/stephengold/V-Sport/blob/master/LICENSE "V-Sport license"
[project]: https://github.com/stephengold/V-Sport "V-Sport Project"
[vulkan]: https://www.vulkan.org/ "Vulkan API"
