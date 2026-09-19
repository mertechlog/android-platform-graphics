# Complete Frame Lifecycle

## Objective

Understand how one graphical frame travels through Android from the moment a display refresh is requested until the display hardware scans the resulting pixels out to the panel.

This page connects:

```text
VSync
  ↓
Choreographer
  ↓
Application UI
  ↓
RenderThread
  ↓
GPU
  ↓
Buffer
  ↓
BufferQueue
  ↓
SurfaceFlinger
  ↓
Composition
  ↓
Hardware Composer
  ↓
Present
  ↓
Display Scanout
```

The key question is:

> **What happens to one frame, when does it happen, and who owns each step?**

---

# 1. The Complete Reference Model

Use this as the reference lifecycle for the rest of the graphics study:

```text
                    DISPLAY
                       │
                       │ VSync
                       ▼
                Choreographer
                       │
                       │ frame callback
                       ▼
                Application UI
                       │
                       │ rendering work
                       ▼
                  RenderThread
                       │
                       │ draw commands
                       ▼
                      GPU
                       │
                       │ writes
                       ▼
                  GPU Buffer
                       │
                       │ queue
                       ▼
                 BufferQueue
                       │
                       │ acquire
                       ▼
                SurfaceFlinger
                       │
                       │ latch + compose
                       ▼
             Hardware Composer
                       │
                       │ validate / present
                       ▼
                Display Hardware
                       │
                       │ scanout
                       ▼
                 Physical Panel
```

This is a **conceptual reference model**, not a literal function-call sequence.

Not every frame follows exactly the same path.

For example:

* SurfaceFlinger may compose using GPU resources.
* HWC may directly compose some layers using hardware planes.
* Some layers may be client-composed while others are device-composed.
* A frame may contain unchanged layers whose existing buffers are reused.
* Different Android versions and hardware implementations can change internal details.

The model is therefore about **responsibility and lifecycle**, not a strict call stack.

---

# 2. What Is a Frame?

A frame is the visual content intended to be presented during one display update.

For example:

```text
Frame N
┌─────────────────────────────┐
│ Status Bar                  │
│                             │
│ Video                       │
│                             │
│ Dialog                      │
│                             │
│ Navigation                  │
└─────────────────────────────┘
```

A frame may involve multiple buffers/layers:

```text
Frame N
 ├── Status Bar buffer
 ├── Video buffer
 ├── Dialog buffer
 └── Navigation buffer
```

Therefore:

```text
Frame ≠ Buffer
```

A frame is a presentation concept.

A buffer is a memory/resource containing graphical data.

One frame may use multiple buffers.

---

# 3. Display Refresh and VSync

A display refreshes at a particular cadence.

For example:

```text
60 Hz display
```

means approximately:

```text
60 refresh opportunities / second
```

The interval is approximately:

```text
1 / 60 second
≈ 16.67 ms
```

Similarly:

```text
90 Hz ≈ 11.11 ms
120 Hz ≈ 8.33 ms
```

A display synchronization signal, commonly referred to as **VSync**, provides an important timing reference for the graphics pipeline.

Conceptually:

```text
Time ──────────────────────────────────────────────►

       │          │          │          │
       ▼          ▼          ▼          ▼
     VSync      VSync      VSync      VSync
```

VSync is not itself a rendered frame.

It is a synchronization/timing event.

---

# 4. Choreographer

Android's **Choreographer** coordinates application-side frame work with the display timing model.

Conceptually:

```text
Display timing
      │
      │ VSync
      ▼
Choreographer
      │
      │ frame callback
      ▼
Application
```

Its purpose is to help schedule UI work at appropriate frame boundaries.

Instead of an application continuously drawing at arbitrary times, the UI pipeline can synchronize frame work with the display's timing.

Conceptually:

```text
VSync
  │
  ▼
Choreographer callback
  │
  ▼
UI traversal
  │
  ▼
Draw
```

The exact scheduling path contains additional framework components and timing stages, but this is the important lifecycle relationship.

---

# 5. Application UI Work

After the frame callback, application-side work can occur.

Examples include:

```text
Input processing
     ↓
State update
     ↓
Measure
     ↓
Layout
     ↓
Draw
```

For a simple animation:

```text
Frame N:
x = 100

Frame N+1:
x = 110

Frame N+2:
x = 120
```

The application updates its visual state and requests the graphical system to produce the corresponding visual content.

The application does not directly control:

```text
SurfaceFlinger
HWC
DRM/KMS
Display scanout
```

It produces graphical content through Android's graphics APIs.

---

# 6. Rendering

Rendering converts application drawing commands into graphical content.

For example:

```text
Application
    │
    │ draw text
    │ draw image
    │ draw shapes
    ▼
Rendering system
    │
    ▼
Pixel data
```

In modern Android, application rendering commonly involves:

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
```

This is a conceptual path.

Not every Android rendering scenario is identical.

---

# 7. RenderThread

**RenderThread** is an Android runtime thread used for rendering-related work for the UI rendering pipeline.

A useful simplified model is:

```text
UI Thread
   │
   │ rendering work
   ▼
RenderThread
   │
   ▼
Graphics rendering
   │
   ▼
GPU
```

The important point is that:

```text
UI logic
```

and:

```text
rendering execution
```

are not necessarily performed entirely on the same thread.

This separation helps Android perform rendering work without making the application's main thread responsible for every low-level rendering operation.

---

# 8. Skia and GPU Rendering

Android uses **Skia** as a major graphics rendering library.

Conceptually:

```text
RenderThread
     │
     ▼
   Skia
     │
     ▼
Graphics API / GPU path
     │
     ▼
   GPU
```

The GPU executes rendering work such as:

```text
Geometry
Text
Textures
Images
Shaders
Blending
```

The result is written into a graphical buffer.

For example:

```text
GPU
 │
 │ render
 ▼
┌────────────────────┐
│                    │
│   Rendered Image   │
│                    │
└────────────────────┘
       Buffer
```

---

# 9. Rendering Produces a Buffer

After rendering, the graphical content exists in a buffer/resource that can be consumed by the composition system.

Conceptually:

```text
Rendering
    │
    ▼
Buffer
```

The buffer has properties such as:

```text
Width
Height
Format
Stride
Usage
Memory allocation
```

For example:

```text
Buffer:
1920 × 1080
RGBA format
GPU/display usage
```

The buffer is not yet necessarily on the physical display.

It is graphical content waiting to participate in composition/presentation.

---

# 10. BufferQueue

Android uses **BufferQueue** as a producer-consumer mechanism for graphical buffers.

The simplified model is:

```text
Producer
   │
   │ dequeue
   ▼
 Buffer
   │
   │ render
   ▼
 Buffer
   │
   │ queue
   ▼
BufferQueue
   │
   │ acquire
   ▼
Consumer
```

In a typical application display path:

```text
Application / Render pipeline
            │
            │ Producer
            ▼
       BufferQueue
            │
            │ Consumer
            ▼
      SurfaceFlinger
```

The producer and consumer do not have to execute synchronously.

This is one of the fundamental properties of Android's graphics architecture.

---

# 11. Why BufferQueue Exists

Rendering and composition happen at different rates and times.

Suppose the application is rendering:

```text
Frame A
Frame B
Frame C
```

while SurfaceFlinger is consuming:

```text
Frame A
```

The system needs a mechanism to safely exchange buffers without both sides writing/reading the same buffer simultaneously.

BufferQueue provides the lifecycle around these buffers.

Conceptually:

```text
Application
    │
    │ produce
    ▼
┌──────────────────┐
│   BufferQueue    │
│                  │
│ Buffer 1         │
│ Buffer 2         │
│ Buffer 3         │
└────────┬─────────┘
         │
         │ consume
         ▼
   SurfaceFlinger
```

The actual implementation contains more state and synchronization than this simplified diagram.

---

# 12. Dequeue → Render → Queue

A producer-side buffer lifecycle can be simplified as:

```text
DEQUEUE
   │
   ▼
AVAILABLE FOR RENDERING
   │
   ▼
RENDER
   │
   ▼
QUEUE
   │
   ▼
AVAILABLE TO CONSUMER
```

Example:

```text
Buffer 1
   ↓
dequeue
   ↓
GPU renders Frame N
   ↓
queue
   ↓
SurfaceFlinger can acquire it
```

The producer should not modify a buffer while the consumer is using it.

Synchronization mechanisms coordinate this ownership transition.

---

# 13. Synchronization

GPU and display operations are asynchronous.

For example:

```text
CPU
 │
 │ submit GPU work
 ▼
GPU
 │
 │ still rendering
 │
 ▼
GPU complete
```

The CPU cannot simply assume that the GPU has completed rendering immediately after issuing commands.

Therefore Android graphics uses synchronization mechanisms/fences to coordinate operations.

Conceptually:

```text
Producer
   │
   │ GPU work
   ▼
  GPU
   │
   │ completion signal
   ▼
 Fence / synchronization
   │
   ▼
Consumer
```

The important principle is:

> **A buffer must not be consumed before the work required to make its contents valid has completed.**

---

# 14. SurfaceFlinger

**SurfaceFlinger** is the Android system component responsible for managing and composing graphical layers for display presentation.

At a conceptual level:

```text
Application Buffers
       │
       ▼
 SurfaceFlinger
       │
       ├── Layer state
       ├── Buffer state
       ├── Geometry
       ├── Visibility
       ├── Z-order
       ├── Alpha
       └── Composition decision
       │
       ▼
Composition
```

SurfaceFlinger is not simply a "copy pixels to screen" component.

It manages the composition lifecycle and coordinates with the display composition system.

---

# 15. SurfaceFlinger Latches Buffers

When SurfaceFlinger acquires a new buffer for a layer, it effectively makes that buffer the content to be considered for subsequent composition.

This is commonly described as **latching** the buffer.

Conceptually:

```text
BufferQueue
     │
     │ queued buffer
     ▼
SurfaceFlinger
     │
     │ acquire
     ▼
  Latch buffer
     │
     ▼
Layer's current content
```

This is important because the application may already be rendering the next buffer while SurfaceFlinger is using the current one.

---

# 16. One Layer, Multiple Buffers

A layer does not necessarily have only one buffer.

For example:

```text
Layer: Video

Buffer A → Frame 100
Buffer B → Frame 101
Buffer C → Frame 102
```

The producer may render:

```text
Frame 102
```

while SurfaceFlinger is currently displaying:

```text
Frame 101
```

Conceptually:

```text
Producer
   │
   ├── Buffer A
   ├── Buffer B
   └── Buffer C
          │
          ▼
      BufferQueue
          │
          ▼
   SurfaceFlinger
          │
          ▼
     Current layer
```

This decoupling is essential for smooth rendering.

---

# 17. SurfaceFlinger Builds the Composition

A display can contain many layers:

```text
Status Bar
Video
Navigation
Dialog
Keyboard
System Overlay
```

SurfaceFlinger considers their state:

```text
Layer
 ├── Buffer
 ├── Position
 ├── Crop
 ├── Transform
 ├── Alpha
 ├── Z-order
 └── Visibility
```

It then determines how the layers should be presented.

Conceptually:

```text
Layer A ─┐
Layer B ─┤
Layer C ─┼──► Composition
Layer D ─┤
Layer E ─┘
```

---

# 18. Composition Is Not Always GPU Composition

This is a critical point.

There are multiple possible composition paths.

### Client/GPU Composition

Conceptually:

```text
Layers
  │
  ▼
GPU
  │
  ▼
Final composition buffer
  │
  ▼
Display
```

### Hardware Composition

Some layers can potentially be handled directly by display hardware through hardware planes:

```text
Layer A ─────► Hardware Plane A
Layer B ─────► Hardware Plane B
Layer C ─────► Hardware Plane C
                       │
                       ▼
                 Display Output
```

### Mixed Composition

Some layers may be GPU-composed while others are handled directly by display hardware:

```text
Layer A ──► GPU ──┐
                  │
Layer B ──────────┼──► Display Hardware
                  │
Layer C ──────────┘
```

The actual decision depends on hardware capabilities and composition constraints.

---

# 19. Hardware Composer

The **Hardware Composer (HWC)** interface connects SurfaceFlinger with the hardware display composition/presentation capabilities.

Conceptually:

```text
SurfaceFlinger
      │
      │ composition configuration
      ▼
     HWC
      │
      ▼
Display Hardware
```

HWC can communicate information such as:

```text
Layer geometry
Crop
Transform
Alpha
Buffer
Composition type
Display configuration
```

The hardware composer implementation determines what the display hardware can handle.

---

# 20. Composition Decision

A simplified decision looks like:

```text
                Layers
                   │
                   ▼
          Can hardware handle
             these layers?
              /         \
            YES          NO
             │            │
             ▼            ▼
       Device/HWC      Client/GPU
       composition     composition
             │            │
             └─────┬──────┘
                   ▼
                Present
```

This is a conceptual simplification.

Real HWC implementations have detailed capability and validation mechanisms.

The important idea is:

> **SurfaceFlinger determines the composition state; HWC provides the hardware-facing composition/presentation capability.**

---

# 21. HWC Validation and Presentation

Conceptually, HWC can be involved in two major stages:

```text
Validate
   │
   ▼
Composition configuration accepted
   │
   ▼
Present
```

Validation answers approximately:

> Can the requested layer composition be handled by the hardware in this configuration?

Presentation means:

> Submit the composition for display at the appropriate time.

The exact API details depend on the Android HWC version and implementation.

---

# 22. Present

**Present** is the point where a completed composition is handed into the display pipeline for presentation.

Conceptually:

```text
Composition
     │
     ▼
   Present
     │
     ▼
Display Hardware
```

Present does not mean:

```text
pixels instantly appear on the panel
```

There is still a hardware/display timing process between submission and physical scanout.

---

# 23. Scanout

**Scanout** is performed by display hardware.

The display hardware reads the configured image data and generates the electrical/video signal sent toward the display panel.

Conceptually:

```text
Display Buffer
      │
      │ memory reads
      ▼
Display Controller
      │
      │ pixel stream
      ▼
Panel Interface
      │
      ▼
Physical Display
```

The important distinction is:

```text
Composition
    =
combine content

Scanout
    =
read/display the resulting content
```

---

# 24. Composition vs Scanout

These are frequently confused.

### Composition

```text
Layer A ─┐
Layer B ─┼──► Combine
Layer C ─┘
```

### Scanout

```text
Composed image
      │
      ▼
Display hardware reads it
      │
      ▼
Pixel stream
      │
      ▼
Panel
```

Therefore:

```text
Composition ≠ Scanout
```

A display controller may also scan out multiple hardware planes directly without first producing one single software-composited buffer.

---

# 25. Complete Single-Frame Timeline

Consider a 60 Hz display.

One conceptual frame interval is:

```text
0 ms                                      16.67 ms
│---------------------------------------------│
│                                             │
VSync                                         VSync
│                                             │
▼                                             ▼
Frame N                                       Frame N+1
```

Within the interval, work may conceptually occur like:

```text
VSync
  │
  ▼
Choreographer
  │
  ▼
UI update
  │
  ▼
RenderThread
  │
  ▼
GPU rendering
  │
  ▼
Buffer queued
  │
  ▼
SurfaceFlinger acquires/latches
  │
  ▼
Composition decision
  │
  ▼
HWC
  │
  ▼
Present
  │
  ▼
Display scanout
```

The actual pipeline is pipelined and asynchronous.

Therefore these operations do not necessarily fit neatly inside one VSync interval in a simple sequential manner.

---

# 26. Pipelining

Graphics is heavily pipelined.

While the display is consuming one frame:

```text
Display:
Frame N
```

the GPU might be rendering:

```text
Frame N+1
```

and the application might already be preparing:

```text
Frame N+2
```

Conceptually:

```text
TIME ───────────────────────────────────────────►

Application:
       Frame N+2
             ──────────────►

GPU:
   Frame N+1
       ───────────────►

Display:
Frame N
──────────────►
```

This is why the graphics pipeline cannot be understood as:

```text
render
  ↓
display
  ↓
render next
```

Instead, multiple frames can be at different stages simultaneously.

---

# 27. Triple Buffering as a Concept

A simplified buffer model might contain:

```text
Buffer A
Buffer B
Buffer C
```

At some moment:

```text
Buffer A → Display/Consumer
Buffer B → Available
Buffer C → GPU rendering
```

Later:

```text
Buffer A → Available
Buffer B → Display/Consumer
Buffer C → GPU rendering
```

The exact number and behavior of buffers depends on the producer/consumer configuration and pipeline requirements.

The important concept is:

> **Multiple buffers allow producer and consumer to operate asynchronously.**

---

# 28. What Happens When Rendering Is Too Slow?

Suppose the display interval is:

```text
16.67 ms
```

but the application/GPU needs:

```text
25 ms
```

to produce the next frame.

The next display opportunity may arrive before the new frame is ready.

Conceptually:

```text
VSync 1
 │
 ├── render starts
 │
 │    25 ms
 │
VSync 2
 │
 └── new frame NOT ready
```

The display may continue using previously available content rather than showing a newly rendered frame.

This can result in visible:

* missed frame opportunity
* reduced animation smoothness
* increased latency

The exact behavior depends on the scheduling and buffer state.

---

# 29. Frame Timing Is More Than Rendering Time

A frame's lifecycle includes many stages:

```text
Input
  ↓
UI processing
  ↓
Animation
  ↓
Layout
  ↓
Draw
  ↓
GPU rendering
  ↓
Buffer availability
  ↓
SurfaceFlinger processing
  ↓
Composition
  ↓
Present
  ↓
Scanout
```

Therefore:

```text
Frame latency
```

is not simply:

```text
GPU rendering time
```

It is the accumulated timing of multiple pipeline stages.

---

# 30. Ownership at Each Stage

A useful ownership model is:

| Stage                | Primary responsibility            |
| -------------------- | --------------------------------- |
| VSync timing         | Display/synchronization system    |
| Frame scheduling     | Choreographer/framework           |
| UI state             | Application/framework             |
| Rendering commands   | Application/rendering pipeline    |
| Render execution     | RenderThread / graphics stack     |
| Pixel generation     | GPU or rendering hardware         |
| Buffer exchange      | BufferQueue                       |
| Layer management     | SurfaceFlinger                    |
| Composition decision | SurfaceFlinger + HWC capabilities |
| Hardware composition | HWC/display hardware              |
| Presentation         | HWC/display pipeline              |
| Scanout              | Display controller/hardware       |
| Physical pixels      | Display panel                     |

This table describes conceptual ownership, not exclusive implementation boundaries.

---

# 31. Control Path vs Pixel Path

The graphics pipeline contains two fundamentally different kinds of information.

### Control/State Path

This describes:

```text
Which layer?
Where?
How large?
Which buffer?
Crop?
Transform?
Alpha?
Z-order?
Visible?
```

Conceptually:

```text
Application
    │
    ▼
Framework
    │
    ▼
SurfaceControl / Layer State
    │
    ▼
SurfaceFlinger
    │
    ▼
HWC
```

### Pixel/Data Path

This describes:

```text
Actual graphical content
```

Conceptually:

```text
Application rendering
       │
       ▼
      GPU
       │
       ▼
    Buffer
       │
       ▼
SurfaceFlinger / HWC
       │
       ▼
Display
```

These paths interact but should not be mentally collapsed into one path.

---

# 32. One Frame with Multiple Layers

Suppose the display contains:

```text
Background
Video
Dialog
Navigation
```

The application renders the video into:

```text
Video Buffer
```

The dialog may have:

```text
Dialog Buffer
```

SurfaceFlinger manages their composition state:

```text
Video Layer
 ├── Video Buffer
 ├── Position
 ├── Crop
 ├── Transform
 └── Z-order

Dialog Layer
 ├── Dialog Buffer
 ├── Position
 ├── Alpha
 └── Z-order
```

The composition system combines them according to the hardware/software composition path.

The resulting presentation is:

```text
┌──────────────────────────────┐
│ Navigation / System UI       │
│                              │
│       Video                  │
│      ┌─────────────┐         │
│      │   Dialog    │         │
│      └─────────────┘         │
│                              │
└──────────────────────────────┘
```

---

# 33. Frame Lifecycle: Complete View

The complete conceptual lifecycle is:

```text
                 DISPLAY
                    │
                    │ VSync
                    ▼
              Choreographer
                    │
                    │ schedule frame
                    ▼
              Application UI
                    │
                    │ state + draw
                    ▼
               RenderThread
                    │
                    ▼
                  Skia
                    │
                    ▼
                   GPU
                    │
                    │ render
                    ▼
                  Buffer
                    │
                    │ queue
                    ▼
               BufferQueue
                    │
                    │ acquire
                    ▼
              SurfaceFlinger
                    │
                    │ latch
                    ▼
               Layer State
                    │
                    │ composition
                    ▼
                  HWC
              ┌─────┴─────┐
              │           │
              ▼           ▼
          GPU/client   HW planes
          composition  / device
              │           │
              └─────┬─────┘
                    ▼
                  Present
                    │
                    ▼
             Display Controller
                    │
                    ▼
                 Scanout
                    │
                    ▼
              Physical Panel
```

---

# 34. What Each Major Component Actually Does

## Choreographer

```text
Coordinates application frame work with display timing.
```

## RenderThread

```text
Executes rendering-related work for the UI rendering pipeline.
```

## Skia

```text
Provides major 2D graphics rendering functionality.
```

## GPU

```text
Executes graphics rendering/computation and may participate in composition.
```

## BufferQueue

```text
Coordinates producer/consumer exchange of graphical buffers.
```

## SurfaceFlinger

```text
Manages graphical layers and coordinates their composition/presentation.
```

## HWC

```text
Provides the hardware-facing composition and display presentation interface.
```

## Display Hardware

```text
Consumes configured image/plane data and performs display scanout.
```

## Panel

```text
Converts the display signal into visible physical pixels.
```

---

# 35. Important Distinctions

### VSync vs Frame

```text
VSync = timing/synchronization event

Frame = visual content intended for presentation
```

### Rendering vs Composition

```text
Rendering = generate content

Composition = combine layers
```

### Buffer vs Frame

```text
Buffer = graphical memory/resource

Frame = presentation unit
```

### BufferQueue vs Buffer

```text
Buffer = actual graphical resource

BufferQueue = mechanism managing producer/consumer exchange
```

### SurfaceFlinger vs HWC

```text
SurfaceFlinger = system-level layer/composition manager

HWC = hardware-facing composition/presentation interface
```

### Present vs Scanout

```text
Present = submit/configure content for display presentation

Scanout = display hardware reads/sends pixels toward the panel
```

---

# 36. The Most Important Concept: The Pipeline Is Asynchronous

Never imagine Android graphics as:

```text
Application
    ↓
GPU
    ↓
SurfaceFlinger
    ↓
HWC
    ↓
Display
```

where every stage waits for the previous stage to finish completely.

A better model is:

```text
             ┌───────────────┐
             │ Application   │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │ RenderThread  │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │     GPU       │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │   Buffers     │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │SurfaceFlinger │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │     HWC       │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │    Display    │
             └───────────────┘

        Multiple frames can be
        at different stages simultaneously.
```

This is the foundation for understanding:

* frame latency
* jank
* missed frames
* buffer starvation
* backpressure
* GPU stalls
* SurfaceFlinger scheduling
* present timing
* display pipeline latency

---

# 37. Final Mental Model

When debugging or studying Android graphics, ask these questions in order:

```text
1. When did the frame become due?
        │
        ▼
2. Did Choreographer schedule the work?
        │
        ▼
3. Did the application produce the required content?
        │
        ▼
4. Did RenderThread execute the rendering work?
        │
        ▼
5. Did the GPU finish rendering?
        │
        ▼
6. Was the buffer successfully queued?
        │
        ▼
7. Did SurfaceFlinger acquire/latch it?
        │
        ▼
8. How was the layer composed?
        │
        ▼
9. Could HWC handle the composition?
        │
        ▼
10. Was the composition presented?
        │
        ▼
11. Did display hardware scan it out?
        │
        ▼
12. Did the panel physically display it?
```

This gives a practical debugging framework.

If a frame is missing, do not immediately blame the GPU.

Determine **which stage failed to meet its timing or synchronization requirement**.

---

# Summary

A frame is not produced and displayed in one atomic operation.

It moves through a pipelined system:

```text
VSync
  ↓
Choreographer
  ↓
Application
  ↓
RenderThread
  ↓
Rendering / GPU
  ↓
Buffer
  ↓
BufferQueue
  ↓
SurfaceFlinger
  ↓
Latch
  ↓
Composition
  ↓
HWC
  ↓
Present
  ↓
Display Hardware
  ↓
Scanout
  ↓
Panel
```

The core responsibilities are:

```text
Choreographer
    → frame timing/scheduling

Application
    → visual state and drawing requests

RenderThread / Rendering stack
    → execute rendering work

GPU
    → generate graphical content and potentially compose

BufferQueue
    → producer/consumer buffer exchange

SurfaceFlinger
    → manage layers and composition/presentation state

HWC
    → hardware-facing composition/presentation

Display hardware
    → consume image/planes and perform scanout

Panel
    → produce physical visible pixels
```

The most important mental model is:

```text
RENDERING
    =
create graphical content

BUFFER
    =
carry graphical content

SURFACEFLINGER
    =
manage layers and composition

COMPOSITION
    =
combine graphical content

HWC
    =
connect composition/presentation to display hardware

PRESENT
    =
submit content for display

SCANOUT
    =
display hardware reads/sends pixels

DISPLAY
    =
physical visual output
```

And the most important timing principle is:

> **Android graphics is a pipelined, asynchronous system in which application rendering, buffer exchange, composition, presentation, and display scanout can all be operating on different frames at the same time.**
