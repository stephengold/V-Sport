<img height="150" src="https://i.imgur.com/YEPFEcx.png" alt="Libbulletjme Project logo">

[The V-Sport Project][project] implements
a [Vulkan]-based graphics engine
for the [Libbulletjme 3-D physics library][libbulletjme].

It contains 2 sub-projects:

1. lib: the V-Sport graphics engine
2. apps: demos, tutorial examples, and non-automated test software

Complete source code (in [Java]) is provided under
[a BSD 3-Clause license][license].


<a name="toc"></a>

## Contents of this document

+ [About V-Sport](#about)
+ [How to build and run V-Sport from source](#build)
+ [What's missing](#todo)
+ [Acknowledgments](#acks)


<a name="about"></a>

## About V-Sport

V-Sport is a Simple Phyisics-ORienTed graphics engine written in Java 1.8.
In addition to Libbulletjme,
it uses [LWJGL], [Assimp], [GLFW], [JOML], and [Vulkan].
It has been tested on Windows, Linux, and macOS.

[Jump to the table of contents](#toc)


<a name="build"></a>

## How to build and run V-Sport from source

### Initial build

1. Install a [Java Development Kit (JDK)][adoptium],
   if you don't already have one.
2. Point the `JAVA_HOME` environment variable to your JDK installation:
   (The path might be something like "C:\Program Files\Java\jre1.8.0_301"
   or "/usr/lib/jvm/java-8-openjdk-amd64/" or
   "/Library/Java/JavaVirtualMachines/liberica-jdk-17-full.jdk/Contents/Home" .)
  + using Bash or Zsh: `export JAVA_HOME="` *path to installation* `"`
  + using Fish: `set -g JAVA_HOME "` *path to installation* `"`
  + using Windows Command Prompt: `set JAVA_HOME="` *path to installation* `"`
  + using PowerShell: `$env:JAVA_HOME = '` *path to installation* `'`
3. Download and extract the V-Sport source code from GitHub:
  + using Git:
    + `git clone https://github.com/stephengold/V-Sport.git`
    + `cd V-Sport`
4. Run the [Gradle] wrapper:
  + using Bash or Fish or PowerShell or Zsh: `./gradlew build`
  + using Windows Command Prompt: `.\gradlew build`

### Tutorials

The tutorial apps all have names starting with "Hello".
For instance, the first tutorial app is named "HelloSport".

To execute "HelloSport":
+ using Bash or Fish or PowerShell or Zsh: `./gradlew HelloSport`
+ using Windows Command Prompt: `.\gradlew HelloSport`

### Demos

Seven demo applications are included:
+ ConveyorDemo
+ NewtonsCradle
+ Pachinko
+ SplitDemo
+ TestGearJoint
+ ThousandCubes
+ Windlass

Documentation for the demo apps is at
https://stephengold.github.io/Libbulletjme/lbj-en/English/demos.html

### Chooser

A Swing-based chooser application is included.
However, it doesn't work yet on macOS.

To run the chooser:
+ using Bash or Fish or PowerShell or Zsh: `./gradlew AppChooser`
+ using Windows Command Prompt: `.\gradlew AppChooser`

### Cleanup

You can restore the project to a pristine state:
+ using Bash or Fish or PowerShell or Zsh: `./gradlew clean`
+ using Windows Command Prompt: `.\gradlew clean`

Note:  these commands will delete any downloaded native libraries.

[Jump to the table of contents](#toc)


<a name="todo"></a>

## What's missing

This project is incomplete.
Future enhancements might include:

+ handle more than 1600 geometries
+ dynamic meshes (for visualizing soft bodies)
+ pre-compiled shaders
+ graphics and physics on separate threads
+ graphical user interface
+ automated tests
+ shadow rendering
+ physically-based rendering
+ more performance statistics
+ sound effects
+ skeletal animation
+ run on mobile platforms (Android and/or iOS)

[Jump to the table of contents](#toc)


<a name="acks"></a>

## Acknowledgments

Portions of the V-Sport Project are derived from [Vulkan-Tutorial-Java][vtj]
by Cristian Herrera, which was in turn ported from
[Alexander Overvoorde's Vulkan tutorial][vt].
I am deeply grateful for all the work that went into these invaluable tutorials.

The ConveyorDemo app derives from sourcecode contributed by "qwq".
The ThousandCubes app derives from sourcecode contributed by Yanis Boudiaf.

This project has made use of the following libraries and software tools:

  + the [Firefox] web browser
  + the [Git] revision-control system and GitK commit viewer
  + the [GitKraken] client
  + the [GLFW] library
  + the [Gradle] build tool
  + the [Java] compiler, standard doclet, and runtime environment
  + [the Java OpenGL Math Library][joml]
  + [the Lightweight Java Gaming Library][lwjgl]
  + the [Markdown] document-conversion tool
  + the [NetBeans] integrated development environment
  + [the Open Asset Import (Assimp) Library][assimp]
  + [the shaderc project][shaderc]
  + Microsoft Windows
  + the [Vulkan] API

I am grateful to [GitHub] and [Imgur]
for providing free hosting for this project
and many other open-source projects.

I'm also grateful to my dear Holly, for keeping me sane.

If I've misattributed anything or left anyone out, please let me know, so I can
correct the situation: sgold@sonic.net

[Jump to the table of contents](#toc)


[adoptium]: https://adoptium.net/releases.html "Adoptium Project"
[assimp]: https://www.assimp.org/ "The Open Asset Importer Library"
[firefox]: https://www.mozilla.org/en-US/firefox "Firefox"
[git]: https://git-scm.com "Git"
[github]: https://github.com "GitHub"
[gitkraken]: https://www.gitkraken.com "GitKraken client"
[glfw]: https://www.glfw.org "GLFW Library"
[gradle]: https://gradle.org "Gradle Project"
[imgur]: https://imgur.com/ "Imgur"
[java]: https://en.wikipedia.org/wiki/Java_(programming_language) "Java programming language"
[joml]: https://joml-ci.github.io/JOML "Java OpenGL Math Library"
[libbulletjme]: https://stephengold.github.io/Libbulletjme/lbj-en/English/overview.html "Libbulletjme Project"
[license]: https://github.com/stephengold/V-Sport/blob/master/LICENSE "V-Sport license"
[lwjgl]: https://www.lwjgl.org "Lightweight Java Game Library"
[markdown]: https://daringfireball.net/projects/markdown "Markdown Project"
[netbeans]: https://netbeans.org "NetBeans Project"
[project]: https://github.com/stephengold/V-Sport "V-Sport Project"
[shaderc]: https://github.com/google/shaderc "shaderc project"
[vt]: https://vulkan-tutorial.com/
[vtj]: https://github.com/Naitsirc98/Vulkan-Tutorial-Java "Vulkan tutorial in Java"
[vulkan]: https://www.vulkan.org/ "Vulkan API"
