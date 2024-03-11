<img height="150" src="https://i.imgur.com/YEPFEcx.png" alt="Libbulletjme Project logo">

[The V-Sport Project][project] implements
a [Vulkan]-based graphics engine
for the [Libbulletjme 3-D physics library][libbulletjme].

It contains 2 sub-projects:

1. lib: the V-Sport graphics engine (a single JVM runtime library)
2. apps: demos, tutorial examples, and non-automated test software

Complete source code (in [Java]) is provided under
[a 3-clause BSD license][license].


<a name="toc"></a>

## Contents of this document

+ [About V-Sport](#about)
+ [How to add V-Sport to an existing project](#add)
+ [How to build and run V-Sport from source](#build)
+ [What's missing](#todo)
+ [Acknowledgments](#acks)


<a name="about"></a>

## About V-Sport

V-Sport is a Simple Physics-ORienTed graphics engine written in Java 1.8.
In addition to Libbulletjme,
it uses [LWJGL], [Assimp], [GLFW], [JOML], and [Vulkan].
It has been tested on Windows, Linux, and macOS.

[Jump to the table of contents](#toc)


<a name="add"></a>

## How to add V-Sport to an existing project

V-Sport comes pre-built as a single library that depends on [Libbulletjme].
However, the Libbulletjme dependency is intentionally omitted from V-Sport's POM
so developers can specify *which* Libbulletjme library should be used.

For projects built using [Maven] or [Gradle], it is
*not* sufficient to specify the
dependency on the V-Sport Library.
You must also explicitly specify the Libbulletjme dependency.

### Gradle-built projects

Add to the project’s "build.gradle" file:

    repositories {
        mavenCentral()
    }
    dependencies {
        implementation 'com.github.stephengold:Libbulletjme:20.1.0'
        implementation 'com.github.stephengold:V-Sport:0.9.0'
    }

For some older versions of Gradle,
it's necessary to replace `implementation` with `compile`.

### Maven-built projects

Add to the project’s "pom.xml" file:

    <repositories>
      <repository>
        <id>mvnrepository</id>
        <url>https://repo1.maven.org/maven2/</url>
      </repository>
    </repositories>

    <dependency>
      <groupId>com.github.stephengold</groupId>
      <artifactId>Libbulletjme</artifactId>
      <version>20.1.0</version>
    </dependency>

    <dependency>
      <groupId>com.github.stephengold</groupId>
      <artifactId>V-Sport</artifactId>
      <version>0.9.0</version>
    </dependency>

### Coding a V-Sport application

Every V-Sport application should extend the `BasePhysicsApp` class,
which provides hooks for:

+ initializing the application,
+ creating and configuring the application's physics space,
+ populating the space with physics objects, and
+ updating the space before each frame is rendered.

The graphics engine doesn't have a scene graph.
Instead, it maintains an internal list of renderable objects,
called *geometries*.
Instantiating a geometry automatically adds it to the list
and causes it to be visualized.

+ To visualize the world (physics-space) coordinate axes,
  instantiate one or more `LocalAxisGeometry` objects.

By default, physics objects are not visualized.

+ To visualize the shape
  of a `PhysicsCollisionObject` other than a `PhysicsSoftBody`,
  invoke the `visualizeShape()` method on the object.
+ To visualize the local coordinate axes of a `PhysicsCollisionObject`,
  invoke the `visualizeAxes()` method on the object.
+ To visualize the wheels of a `PhysicsVehicle`,
  invoke the `visualizeWheels()` method on the vehicle.
+ To visualize the bounding box of a `PhysicsCollisionObject`,
  instantiate an `AabbGeometry` for the object.
+ To visualize a `Constraint`,
  instantiate a `ConstraintGeometry` for each end.
+ To visualize the pins of a `PhysicsSoftBody`,
  instantiate a `PinsGeometry` for the body.
+ To visualize the wind acting on a `PhysicsSoftBody`,
  instantiate a `WindVelocityGeometry` for the body.

[Jump to the table of contents](#toc)


<a name="build"></a>

## How to build and run V-Sport from source

### Initial build

1. Install a [Java Development Kit (JDK)][adoptium],
   if you don't already have one.
2. Point the `JAVA_HOME` environment variable to your JDK installation:
   (In other words, set it to the path of a directory/folder
   containing a "bin" that contains a Java executable.
   That path might look something like
   "C:\Program Files\Eclipse Adoptium\jdk-17.0.3.7-hotspot"
   or "/usr/lib/jvm/java-17-openjdk-amd64/" or
   "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home" .)
  + using Bash or Zsh: `export JAVA_HOME="` *path to installation* `"`
  + using [Fish]: `set -g JAVA_HOME "` *path to installation* `"`
  + using Windows Command Prompt: `set JAVA_HOME="` *path to installation* `"`
  + using PowerShell: `$env:JAVA_HOME = '` *path to installation* `'`
3. Download and extract the V-Sport source code from GitHub:
  + using [Git]:
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
However, it doesn't work on macOS yet.

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

The ConveyorDemo app derives from source code
contributed by "qwq" in March 2022.

The ThousandCubes app derives from source code contributed by Yanis Boudiaf.

This project has made use of the following libraries and software tools:

  + the [Checkstyle] tool
  + the [Firefox] web browser
  + the [Git] revision-control system and GitK commit viewer
  + the [GitKraken] client
  + the [GLFW] library
  + the [Gradle] build tool
  + the [Java] compiler, standard doclet, and runtime environment
  + [the Java OpenGL Math Library][joml]
  + [the Lightweight Java Gaming Library][lwjgl]
  + the [Linux Mint][mint] operating system
  + the [Markdown] document-conversion tool
  + the [Meld] visual merge tool
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
[checkstyle]: https://checkstyle.org "Checkstyle"
[firefox]: https://www.mozilla.org/en-US/firefox "Firefox"
[fish]: https://fishshell.com/ "Fish command-line shell"
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
[maven]: https://maven.apache.org "Maven Project"
[meld]: https://meldmerge.org "Meld merge tool"
[mint]: https://linuxmint.com "Linux Mint Project"
[netbeans]: https://netbeans.org "NetBeans Project"
[project]: https://github.com/stephengold/V-Sport "V-Sport Project"
[shaderc]: https://github.com/google/shaderc "shaderc project"
[vt]: https://vulkan-tutorial.com/
[vtj]: https://github.com/Naitsirc98/Vulkan-Tutorial-Java "Vulkan tutorial in Java"
[vulkan]: https://www.vulkan.org/ "Vulkan API"
