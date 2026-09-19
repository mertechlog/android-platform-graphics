# Android Graphics Architecture Overview

## Objective

Understand the complete Android graphics stack from an application producing visual content to the physical display showing the final pixels.

The key question this page must answer:

> **Who does what in Android graphics?**

The most useful mental model is:

```text
Application
    │
    │ What should be displayed?
    ▼
Android Framework
    │
    │ Manage windows, surfaces, buffers, synchronization
    ▼
Native Graphics
    │
    │ Render, allocate, queue, compose
    ▼
Graphics HAL
    │
    │ Hardware-specific composition / display interface
    ▼
Kernel
    │
    │ DRM / KMS / GPU driver / synchronization
    ▼
Display Hardware
    │
    │ Scanout
    ▼
Physical Pixels
```

Android graphics becomes much easier to understand when separated into two paths:

```text
                 ANDROID GRAPHICS
                       │
          ┌────────────┴────────────┐
          │                         │
     CONTROL PATH              PIXEL PATH
          │                         │
  "What should happen?"       "What pixels move?"
          │                         │
  Windows / Layers /          Rendering / Buffers /
  Geometry / Z-order /        Composition / Scanout
  Visibility / Timing
          │                         │
          └─────────┬───────────────┘
                    ▼
                 Display
```

---

# 1. What Android Graphics Actually Does

At the highest level, Android graphics has three major responsibilities:

1. Rendering
2. Composition
3. Presentation / Scanout

These are related, but they are not the same operation.

---

## 1.1 Rendering

Rendering produces the visual content of a graphical object or layer.

Examples:

* Text
* Buttons
* Images
* Animations
* Game graphics
* Video frames
* UI elements

Typical producers include:

* Application UI
* Skia
* RenderThread
* GPU
* Video decoders
* Camera
* Other hardware/software producers

Rendering answers:

> **How are the pixels of a particular piece of content produced?**

Example:

```text
Application UI
      │
      ▼
 RenderThread
      │
      ▼
    Skia
      │
      ▼
     GPU
      │
      ▼
Application Buffer
```

The result is graphical pixel data stored in a buffer.

---

## 1.2 Composition

Composition combines multiple independently produced graphical layers into the image that should appear on a display.

For example:

```text
Application A
┌────────────────────────────┐
│                            │
│        App Content         │
│                            │
└────────────────────────────┘

Application B
        ┌────────────────────┐
        │                    │
        │      Dialog        │
        │                    │
        └────────────────────┘

System UI
┌────────────────────────────────────┐
│              Status Bar             │
└────────────────────────────────────┘
```

The final display image may look like:

```text
┌────────────────────────────────────┐
│              Status Bar             │
│                                    │
│       Application A                │
│             ┌──────────────┐       │
│             │    Dialog    │       │
│             └──────────────┘       │
│                                    │
└────────────────────────────────────┘
```

Composition answers:

> **How should all visible layers be combined to form the final display image?**

Composition may be performed by:

* GPU
* Display hardware
* A combination of GPU and display hardware

---

## 1.3 Presentation and Scanout

After the display content is ready, the display hardware must consume it according to display timing.

Conceptually:

```text
Composition
     │
     ▼
  Present
     │
     ▼
  Scanout
     │
     ▼
  Display
```

Presentation answers:

> **When and how is the composed frame made available to the display hardware?**

Scanout is the process in which display hardware reads display data and sends the pixel stream toward the display panel.

---

# 2. Complete Android Graphics Stack

A practical conceptual view of the Android graphics architecture is:

```text
┌─────────────────────────────────────────────────────────────┐
│                       APPLICATION                           │
│                                                             │
│ Activity / View / Compose / Game / Video / Camera           │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    ANDROID FRAMEWORK                        │
│                                                             │
│ WindowManager / SurfaceControl / Surface / Choreographer    │
│ BufferQueue interfaces / Synchronization                    │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       NATIVE GRAPHICS                       │
│                                                             │
│ SurfaceFlinger / RenderThread / Skia / BufferQueue          │
│ GraphicBuffer / AHardwareBuffer / Gralloc / Sync            │
└──────────────────────────────┬──────────────────────────────┘
                               │
                 ┌─────────────┴─────────────┐
                 │                           │
                 ▼                           ▼
        ┌──────────────────┐       ┌──────────────────┐
        │       GPU        │       │       HWC        │
        │                  │       │                  │
        │ Rendering        │       │ Composition      │
        │ Composition      │       │ Display control  │
        └────────┬─────────┘       └────────┬─────────┘
                 │                          │
                 └────────────┬─────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     GRAPHICS / DISPLAY HAL                  │
│                                                             │
│ Hardware abstraction for graphics/display functionality     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                          KERNEL                             │
│                                                             │
│ DRM / KMS / GPU Driver / Display Driver / DMA / Sync       │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    DISPLAY HARDWARE                         │
│                                                             │
│ Display Controller → Panel / HDMI / DP / External Display  │
└─────────────────────────────────────────────────────────────┘
```

This is a **conceptual architecture**, not a strict call stack.

For example, SurfaceFlinger does not simply call the kernel directly for every graphics operation. Different operations use different interfaces and components.

---

# 3. Application Layer

The application is the source of visual content.

Examples:

```text
Activity
View
Compose
Game
Video Player
Camera Application
```

Suppose an application contains:

```text
Text
Image
Button
Animation
```

The application normally does not directly control the physical display.

Instead, it expresses:

> "This is the content I want displayed."

Android then manages how that content becomes a displayable surface and eventually reaches the display.

### Application responsibility

The application primarily owns:

* Application content
* UI state
* Drawing requests
* Application-level rendering behavior

The application normally does not own:

* The complete physical display
* Global layer ordering
* Final display composition
* Display hardware
* Physical scanout

---

# 4. Android Framework Layer

The Android framework provides high-level APIs and system services used to manage graphical content.

Important components include:

```text
WindowManager
SurfaceControl
Surface
Choreographer
View System
Display-related framework APIs
```

The framework deals heavily with the **control side** of graphics.

For example:

```text
Where should a window appear?
What size should it have?
What is its Z-order?
Should it be visible?
Which display should it use?
What surface belongs to it?
When should application work happen?
```

The framework therefore describes and controls the graphical scene.

A useful mental model is:

```text
Application
     │
     │ "I have this content."
     ▼
Framework
     │
     │ "This content belongs to this surface,
     │  with this geometry and visibility."
     ▼
Native Graphics System
```

---

# 5. Native Graphics Layer

The native graphics layer performs much of the actual graphics work.

Important components include:

```text
SurfaceFlinger
RenderThread
Skia
BufferQueue
GraphicBuffer
AHardwareBuffer
Gralloc
Synchronization infrastructure
```

These components have different responsibilities.

---

## 5.1 SurfaceFlinger

SurfaceFlinger is the central Android system component responsible for managing display composition and presenting graphical layers to displays.

Conceptually:

```text
Application Surfaces
        │
        ▼
   SurfaceFlinger
        │
        ├── Layer information
        ├── Geometry
        ├── Visibility
        ├── Buffer state
        ├── Composition decisions
        │
        ▼
   Display Composition
```

SurfaceFlinger is **not the GPU**.

It is primarily responsible for managing the system-wide graphical layer composition and display presentation pipeline.

---

## 5.2 RenderThread

RenderThread is associated with Android application UI rendering.

It executes rendering work for the Android UI rendering pipeline and works with graphics libraries and GPU-backed rendering where appropriate.

Conceptually:

```text
UI / View Hierarchy
       │
       ▼
   RenderThread
       │
       ▼
      Skia
       │
       ▼
      GPU
       │
       ▼
Rendered Buffer
```

The exact rendering path depends on Android version, device configuration, and content.

---

## 5.3 Skia

Skia is a graphics library used extensively by Android for 2D rendering.

It handles drawing operations such as:

```text
Text
Shapes
Paths
Images
Bitmaps
UI elements
```

Important distinction:

```text
Skia        → graphics/drawing library
RenderThread → execution context for UI rendering work
GPU         → hardware processor used for GPU workloads
```

These are related, but they are not interchangeable terms.

---

# 6. Rendering vs Composition

This distinction is fundamental.

## Rendering

Rendering creates the content of a graphical producer or layer.

Example:

```text
Application UI
      │
      ▼
  RenderThread
      │
      ▼
     Skia
      │
      ▼
     GPU
      │
      ▼
Application Buffer
```

Result:

```text
┌───────────────────────┐
│ Application Content   │
│                       │
│    Hello Android      │
│                       │
└───────────────────────┘
```

---

## Composition

Composition combines multiple buffers/layers.

Suppose there are:

```text
Buffer A → Application
Buffer B → Dialog
Buffer C → System UI
```

Composition produces:

```text
┌───────────────────────────────┐
│ System UI                     │
│                               │
│ Application                   │
│          ┌─────────────┐      │
│          │   Dialog    │      │
│          └─────────────┘      │
│                               │
└───────────────────────────────┘
```

### Key distinction

```text
Rendering:
"What should this layer look like?"

Composition:
"How should all layers appear together?"
```

A GPU may participate in both rendering and composition, depending on the device architecture and composition decision.

---

# 7. BufferQueue — Producer / Consumer Bridge

Android graphics commonly uses a producer/consumer buffer model.

A simplified view:

```text
Producer
   │
   │ Dequeue buffer
   ▼
 Buffer
   │
   │ Render
   ▼
Queue buffer
   │
   ▼
BufferQueue
   │
   ▼
Consumer
```

For an application surface, the application-side rendering pipeline can act as a producer, while SurfaceFlinger can consume the produced buffers.

The important concept is:

> **A producer creates or fills a buffer; a consumer uses that buffer.**

This decouples rendering from consumption.

Multiple buffers can allow rendering and display processing to overlap.

The exact number and lifecycle of buffers depend on the pipeline and configuration.

---

# 8. Graphics Hardware Abstraction Layer

Android needs to support many different hardware implementations.

For example:

```text
Qualcomm
MediaTek
Samsung
Other SoC vendors
```

The underlying GPU, display controller, memory architecture, and composition capabilities can differ.

Android therefore uses hardware abstraction interfaces.

Conceptually:

```text
Android Graphics Framework
          │
          ▼
     Graphics HAL
          │
          ▼
Vendor Implementation
          │
          ▼
     Hardware
```

The HAL hides hardware-specific implementation details behind Android-defined interfaces.

For display composition, Hardware Composer (HWC) is especially important.

---

# 9. Hardware Composer — HWC

Hardware Composer provides the hardware-facing interface for display composition and presentation.

SurfaceFlinger manages the Android graphical scene and works with HWC to determine how that scene can be presented by the display hardware.

Conceptually:

```text
                 SurfaceFlinger
                       │
              Composition planning
                       │
                       ▼
                     HWC
                       │
          ┌────────────┴────────────┐
          │                         │
          ▼                         ▼
Hardware Composition          GPU Composition
          │                         │
          ▼                         ▼
Display Hardware             Composed Buffer
```

Important point:

> **SurfaceFlinger and HWC are not simply two independent compositors competing with each other.**

SurfaceFlinger manages the Android composition pipeline and coordinates with HWC.

HWC exposes the display hardware's composition and presentation capabilities.

---

# 10. Hardware Composition vs GPU Composition

A display may contain multiple layers.

For example:

```text
Layer A → Video
Layer B → Application UI
Layer C → Navigation Bar
```

If the display hardware supports the required operations, the layers may be assigned to hardware planes:

```text
Layer A ───────────────► Hardware Plane 0
Layer B ───────────────► Hardware Plane 1
Layer C ───────────────► Hardware Plane 2
                                  │
                                  ▼
                           Display Controller
```

This can avoid an additional GPU composition pass.

If the hardware cannot directly perform the required composition:

```text
Layers
  │
  ▼
GPU Composition
  │
  ▼
One Composed Buffer
  │
  ▼
Display Hardware
```

Therefore:

```text
GPU Composition
       ≠
Hardware Composition
```

The actual composition decision depends on:

* Layer format
* Layer size
* Position
* Crop
* Transform
* Alpha
* Blending requirements
* Hardware capabilities
* Number of available planes
* Bandwidth constraints
* Other platform-specific restrictions

---

# 11. Kernel Graphics Layer

Below Android userspace are Linux kernel graphics components.

Important areas include:

```text
DRM
KMS
GPU Driver
Display Driver
DMA / Memory Management
Synchronization
```

---

## 11.1 DRM

DRM stands for:

> **Direct Rendering Manager**

DRM provides Linux kernel infrastructure for graphics hardware.

It is used by GPU and display subsystems.

---

## 11.2 KMS

KMS stands for:

> **Kernel Mode Setting**

KMS provides mechanisms for configuring the display pipeline.

Conceptually, this can involve:

```text
Display Mode
Connector
CRTC
Plane
Framebuffer
Display Pipeline
```

A simplified path is:

```text
Userspace
   │
   ▼
DRM / KMS
   │
   ▼
Display Controller
   │
   ▼
Panel
```

The exact implementation is platform-dependent.

---

# 12. Display Hardware

The final stage is physical display hardware.

A simplified pipeline is:

```text
Memory
   │
   ▼
Display Controller
   │
   ▼
Display Interface
   │
   ▼
Panel
```

The display controller obtains the required pixel data from memory and processes it according to the configured display pipeline.

The panel ultimately receives the pixel stream and displays the image.

---

# 13. Scanout

**Scanout** is the process in which display hardware reads display data and sends the resulting pixel stream toward the display.

Conceptually:

```text
Display Buffer
      │
      ▼
Display Controller
      │
      │ Read pixels
      ▼
Pixel Stream
      │
      ▼
Panel
```

The display pipeline operates according to display timing.

Important timing concepts include:

```text
Refresh Rate
VSync
Display Timing
Scanout
```

This is why Android graphics is tightly connected to display timing.

---

# 14. Control Path vs Pixel Path

This distinction is one of the most useful mental models for Android graphics.

---

## 14.1 Control Path

The control path describes:

> **What should happen?**

Examples:

```text
Window position
Window size
Layer visibility
Z-order
Crop
Transform
Display assignment
Buffer state
Composition type
Timing information
```

A simplified control path:

```text
Application
    │
    ▼
WindowManager
    │
    ▼
SurfaceControl
    │
    ▼
SurfaceFlinger
    │
    ▼
HWC
    │
    ▼
Display Configuration
```

The control path primarily carries **state, metadata, and decisions**.

---

## 14.2 Pixel Path

The pixel path describes:

> **Where does the actual image data go?**

A simplified path:

```text
Application Rendering
        │
        ▼
      Buffer
        │
        ▼
   BufferQueue
        │
        ▼
 SurfaceFlinger / HWC
        │
        ▼
 GPU / Display Hardware
        │
        ▼
      Scanout
        │
        ▼
      Display
```

The pixel path involves the actual graphical image data.

---

## 14.3 Why the Separation Matters

Suppose a layer moves from:

```text
[0, 0, 800, 600]
```

to:

```text
[100, 100, 900, 700]
```

The pixels inside the buffer may remain unchanged.

The control state changed:

```text
Position changed
```

The existing buffer can potentially be reused rather than requiring the application to redraw the entire content.

This distinction becomes extremely important when studying:

* WindowManager
* SurfaceControl
* SurfaceFlinger
* BufferQueue
* HWC
* GPU composition
* Hardware planes

---

# 15. Who Owns What?

The following is a simplified ownership model.

| Component          | Primary responsibility                                         |
| ------------------ | -------------------------------------------------------------- |
| Application        | Produces graphical content                                     |
| View / Compose     | Describes UI to be rendered                                    |
| Choreographer      | Coordinates application frame work with display timing         |
| RenderThread       | Executes Android UI rendering work                             |
| Skia               | Provides drawing/rendering functionality                       |
| GPU                | Executes GPU rendering and composition workloads               |
| BufferQueue        | Connects buffer producers and consumers                        |
| Surface            | Provides a rendering target abstraction                        |
| SurfaceControl     | Controls surface/layer properties                              |
| WindowManager      | Manages windows and their relationships                        |
| SurfaceFlinger     | Manages system-wide layer composition and display presentation |
| HWC                | Provides hardware composition/display interface                |
| Gralloc            | Integrates graphics buffer allocation and usage                |
| DRM/KMS            | Provides kernel graphics/display infrastructure                |
| GPU Driver         | Interfaces the GPU with the kernel                             |
| Display Controller | Processes display data for scanout                             |
| Panel              | Physically displays the pixels                                 |

This table is intentionally simplified.

The real Android implementation contains additional processes, threads, interfaces, synchronization objects, vendor components, and hardware-specific paths.

---

# 16. Example — Opening an Android Application

Suppose the user opens an application.

A simplified frame/display flow is:

```text
User Interaction
       │
       ▼
Input System
       │
       ▼
Application / Activity
       │
       ▼
View / UI State Changes
       │
       ▼
Choreographer
       │
       ▼
RenderThread
       │
       ▼
Skia / GPU
       │
       ▼
Application Buffer
       │
       ▼
BufferQueue
       │
       ▼
SurfaceFlinger
       │
       ├── Examine layers
       ├── Determine geometry
       ├── Determine visibility
       ├── Evaluate composition
       │
       ▼
HWC
       │
       ├── Hardware composition
       │
       │ OR
       │
       └── GPU-assisted composition
       │
       ▼
Display Hardware
       │
       ▼
Scanout
       │
       ▼
Panel
       │
       ▼
User sees the result
```

This is intentionally simplified.

The actual Android implementation contains asynchronous execution, synchronization, buffer acquisition/release, fences, display timing, and potentially several additional stages.

---

# 17. There Is Not One Single Graphics Pipeline

A common beginner mistake is to imagine:

```text
Application
     ↓
GPU
     ↓
Display
```

That model is incomplete.

Android graphics consists of several cooperating paths:

```text
                  Android Graphics
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
     UI Control      Rendering      Composition
          │              │              │
          │              ▼              │
          │             GPU             │
          │                             │
          └──────────────┬──────────────┘
                         ▼
                    Presentation
                         │
                         ▼
                      Scanout
                         │
                         ▼
                      Display
```

A single frame may involve:

* CPU
* RenderThread
* Skia
* GPU
* BufferQueue
* SurfaceFlinger
* HWC
* DRM/KMS
* Display Controller
* Panel

However, **not every component performs the same job**, and not every frame necessarily uses every component in exactly the same way.

---

# 18. Rendering Path vs Composition Path

Consider three graphical producers:

```text
Application A
Application B
System UI
```

Each can produce its own buffer:

```text
Application A ──► Buffer A
Application B ──► Buffer B
System UI      ──► Buffer C
```

Those buffers may then participate in composition:

```text
Buffer A
Buffer B
Buffer C
   │
   ▼
Composition
   │
   ▼
Display Image
```

Conceptually:

```text
                  RENDERING
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
     Buffer A     Buffer B     Buffer C
        │            │            │
        └────────────┼────────────┘
                     ▼
                 COMPOSITION
                     │
                     ▼
             Display Composition
                     │
                     ▼
                  SCANOUT
```

Therefore:

> **Rendering creates content; composition combines content.**

---

# 19. Frame-Level Mental Model

For one display frame, use this sequence:

```text
1. Determine what needs to change
             ↓
2. Application performs rendering work
             ↓
3. Rendered content becomes available in a buffer
             ↓
4. Buffer becomes available to the consumer
             ↓
5. SurfaceFlinger evaluates visible layers
             ↓
6. Composition strategy is determined
             ↓
7. GPU and/or display hardware performs composition
             ↓
8. Frame is presented
             ↓
9. Display hardware scans out the frame
             ↓
10. Panel displays the pixels
```

This sequence is more important than memorizing individual class names.

---

# 20. Five Questions That Define the Graphics Pipeline

Keep these questions separate.

### Question 1 — What is being drawn?

**Rendering**

```text
Skia / RenderThread / GPU
```

---

### Question 2 — Where does the content belong?

**Window and layer management**

```text
WindowManager / SurfaceControl
```

---

### Question 3 — How do multiple pieces become a display image?

**Composition**

```text
SurfaceFlinger / HWC / GPU
```

---

### Question 4 — How does hardware receive the display configuration and data?

**Display pipeline**

```text
HWC / DRM / KMS / Display Hardware
```

---

### Question 5 — How does the user actually see it?

**Scanout + Panel**

```text
Display Controller
       ↓
Pixel Stream
       ↓
Panel
```

---

# 21. Final Reference Architecture

Use this as the reference model for the rest of the Android graphics study.

```text
┌─────────────────────────────────────────────────────────────┐
│                         APPLICATION                         │
│                                                             │
│ Activity / View / Compose / Game / Video / Camera           │
│                                                             │
│ Produces visual content                                     │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ANDROID FRAMEWORK                        │
│                                                             │
│ WindowManager / Surface / SurfaceControl / Choreographer    │
│                                                             │
│ Controls windows, surfaces, geometry and timing             │
└─────────────────────────────┬───────────────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
                ▼                           ▼
        ┌───────────────┐           ┌────────────────┐
        │   RENDERING   │           │    CONTROL     │
        │               │           │                │
        │ RenderThread  │           │ WindowManager  │
        │ Skia          │           │ SurfaceControl │
        │ GPU           │           │ Layer State    │
        └───────┬───────┘           └───────┬────────┘
                │                           │
                │ Buffer                    │ Layer State
                ▼                           ▼
        ┌─────────────────────────────────────────┐
        │             BUFFER / LAYER SYSTEM       │
        │                                         │
        │ BufferQueue / GraphicBuffer /           │
        │ AHardwareBuffer / Layer State           │
        └────────────────────┬────────────────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │    SURFACEFLINGER    │
                  │                      │
                  │ Collect layers       │
                  │ Evaluate geometry    │
                  │ Evaluate visibility  │
                  │ Determine composition│
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │         HWC          │
                  │                      │
                  │ Hardware composition │
                  │ Display interface    │
                  └──────────┬───────────┘
                             │
                  ┌──────────┴──────────┐
                  │                     │
                  ▼                     ▼
          ┌──────────────┐      ┌───────────────┐
          │     GPU      │      │ Display HW    │
          │              │      │ / Planes      │
          │ Composition  │      │               │
          └──────┬───────┘      └───────┬───────┘
                 │                      │
                 └──────────┬───────────┘
                            ▼
                    ┌───────────────┐
                    │   DRM / KMS   │
                    │    Kernel     │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │Display Control│
                    │    / Scanout  │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │     PANEL     │
                    │ Physical Pixel│
                    └───────────────┘
```

---

# 22. Final Summary

Android graphics is not simply:

```text
Application → GPU → Display
```

A better mental model is:

```text
Application
    ↓
Framework
    ↓
Rendering
    ↓
Buffers
    ↓
SurfaceFlinger
    ↓
Composition Decision
    ↓
GPU and/or HWC
    ↓
DRM/KMS + Display Hardware
    ↓
Scanout
    ↓
Panel
```

The most important separation is:

```text
CONTROL PATH
"What should be displayed and where?"
        │
        ├── Window
        ├── Surface
        ├── Layer
        ├── Geometry
        ├── Visibility
        ├── Z-order
        └── Composition State


PIXEL PATH
"Where does the actual image data go?"
        │
        ├── Rendering
        ├── Buffer
        ├── BufferQueue
        ├── Composition
        ├── Display Buffer
        └── Scanout
```

## One-Line Ownership Model

```text
Application     → creates content
Framework       → manages graphical objects and state
Choreographer   → coordinates frame work with display timing
RenderThread    → executes UI rendering work
Skia            → provides drawing/rendering functionality
GPU             → executes GPU workloads
BufferQueue     → connects buffer producers and consumers
SurfaceFlinger  → manages system-wide composition and presentation
HWC             → interfaces composition with display hardware
DRM/KMS         → provides kernel graphics/display infrastructure
Display HW      → processes and scans out display data
Panel           → physically displays pixels
```

## Core Mental Model

The entire Android graphics system can ultimately be understood through four questions:

```text
1. WHO produces the content?
        ↓
   Application / Rendering Pipeline

2. WHERE does that content live?
        ↓
   Surface / Buffer / Layer

3. HOW are multiple layers combined?
        ↓
   SurfaceFlinger + GPU and/or HWC

4. HOW do pixels reach the user?
        ↓
   Display Hardware → Scanout → Panel
```

The next pages can build on this model without redefining these concepts repeatedly.
