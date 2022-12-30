RedReader Code Modernisation Project 
=========


The RedReader app is absolutely great, and I use it nearly every day.

But the codebase is mostly stuck around 2014, except for the use of Gradle.

This fork's aim is to gradually bring it up to date with modern android development practises.

This should make it easier for other android devs to provide code contributions, 
and should (hopefully) reduce the maintenance burden and development time required to add new features.

I've read `CONTRIBUTING.md`, and it sounds like these changes may not be accepted into the main repository.
Given the time cost to retrain from RedReader's code style to modernity, that is understandable.

But this is an itch I need to scratch either way.

I don't intend to take on the long-term responsibility of maintaining a separate app once/if the refactoring is done,
so I'm hoping someone (maybe QuantumBadger) will be interested in taking this on.

# ROUGH ROADMAP

## Step 1: Doing Now

### Convert all Java to Kotlin

Kotlin brings a number of well-documented advantages over Java 7, which is what this codebase is written in now.

### Replace Reinvented Wheels/Manually-Backported Code

This repo contains a number of classes which duplicate functionality in later versions of Java, 
or which are usually provided by a well-known library in modern android development.

These are an unnecessary burden on the maintainer, and can potentially introduce (or miss fixes for) bugs.

examples identified so far:

* Optional & Consumer
* Stream
* RRTime uses Joda Time, should be migrated to JSR-310 `java.time`
* Stack
* Void
* UnaryOperator
* Predicate

### Replace Old Libraries With Newer Ones

Similar to above but less important, this repo still makes use of older libraries for common tasks,
many of which are now considered suboptimal or deprecated by the Android dev community.

Examples:

* Replace Jackson with Kotlinx.serialize
* Use Retrofit instead of OkHttp directly

## Step 2: To Do Later

### Migrate to MVVM Architecture

## Step 3: Maybe Do, Maybe not

### Migrate to Compose

### Migrate to Room

### Make Everything Testable
### Write Tests
