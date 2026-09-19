# Graphics Terminology

## Objective

Build a precise vocabulary for Android graphics.

This page defines the core terms used throughout the graphics pipeline:

* Pixel
* Frame
* Image
* Buffer
* Surface
* Window
* SurfaceControl
* Layer
* GraphicBuffer
* AHardwareBuffer
* Framebuffer
* Plane
* Display
* Composition
* Rendering
* Scanout
* Present
* Latch

The goal is not just to memorize definitions.

The important goal is to understand:

> **What is the relationship between these objects, and where does each one exist in the Android graphics pipeline?**

---

# 1. Pixel

A **pixel** is one picture element of an image.

For a typical RGB image, one pixel contains color information such as:

```text
Red
Green
Blue
Alpha (if applicable)
```

For example:

```text
Pixel
┌────────────────────┐
│ R = 255            │
│ G = 0              │
│ B = 0              │
│ A = 255            │
└────────────────────┘
```

This represents an opaque red pixel in a typical RGBA representation.

A pixel is therefore **image data**, not a physical display object.

For a 1920 × 1080 image:

```text
1920 × 1080 = 2,073,600 pixels
```

If the format is RGBA8888:

```text
8 bits  → Red
8 bits  → Green
8 bits  → Blue
8 bits  → Alpha

Total = 32 bits = 4 bytes per pixel
```

Ignoring padding, compression, tiling, and other implementation details:

```text
2,073,600 × 4
≈ 8.29 MB
```

### Key point

> **Pixel = smallest logical picture element of an image.**

Do not confuse a pixel with a physical display element. Modern panels can have additional physical subpixel structures beneath the logical pixel abstraction.

---

# 2. Image

An **image** is a two-dimensional collection of pixels representing visual content.

Example:

```text
Image
1920 × 1080

┌─────────────────────────────┐
│ p p p p p p p p p p p p ... │
│ p p p p p p p p p p p p ... │
│ p p p p p p p p p p p p ... │
│ ...                         │
└─────────────────────────────┘
```

An image has properties such as:

* Width
* Height
* Pixel format
* Color space
* Pixel data
* Stride
* Potentially metadata

An image is a **logical visual representation**.

It does not necessarily imply how or where the data is stored in memory.

For example, an image can exist conceptually as:

```text
Decoded image
     ↓
Pixel data
     ↓
Graphics buffer
```

### Key point

> **Image = visual pixel data organized as a 2D picture.**

---

# 3. Frame

A **frame** is the set of visual content intended to be presented for one display update.

For a 1920 × 1080 display:

```text
Frame
┌─────────────────────────────┐
│                             │
│       Complete display      │
│           content           │
│                             │
└─────────────────────────────┘
```

A frame may be:

* Rendered into one buffer
* Produced from multiple buffers
* Composed from multiple layers
* Presented directly by display hardware

Therefore:

> **Frame describes a display update, not necessarily a specific memory object.**

This distinction is important.

A frame is a **conceptual unit of presentation**.

A buffer is a **storage/transport object**.

They are related, but they are not synonymous.

---

# 4. Buffer

A **buffer** is a region of memory used to store data.

In graphics, a buffer commonly stores image/pixel data.

Conceptually:

```text
Buffer
┌─────────────────────────────┐
│ Pixel data                  │
│ Pixel data                  │
│ Pixel data                  │
│ ...                         │
└─────────────────────────────┘
```

A graphics buffer normally has characteristics such as:

```text
Width
Height
Pixel format
Stride
Usage
Memory layout
```

For example:

```text
Width  = 1920
Height = 1080
Format = RGBA8888
```

The buffer can then be used by a graphics producer or consumer.

### Important distinction

```text
Image
  ↓
Logical visual data

Buffer
  ↓
Memory/storage used to hold that data
```

An image is a concept.

A buffer is an implementation/storage resource.

---

# 5. Surface

A **Surface** represents a producer-facing rendering target associated with a graphical destination.

It provides a way for a producer to obtain buffers into which it can render or write content.

Conceptually:

```text
Application
     │
     ▼
  Surface
     │
     ▼
BufferQueue
     │
     ▼
Graphics Buffers
```

A Surface is therefore **not the pixels themselves**.

It is also not the physical display.

A useful mental model is:

```text
Surface
   ↓
"I have a place where I can produce graphical buffers."
```

For example:

```text
Application
     │
     │ renders
     ▼
  Surface
     │
     ▼
BufferQueue
     │
     ▼
SurfaceFlinger
```

### Important distinction

```text
Surface     → producer-side rendering interface
Buffer      → memory containing graphical data
Layer       → composition-side representation
Display     → destination where content is presented
```

These are different concepts.

---

# 6. Window

A **Window** is a framework-level object representing a region or entity that participates in Android window management.

Examples include:

```text
Activity window
Dialog window
System UI window
Input method window
```

A window has properties such as:

* Position
* Size
* Visibility
* Z-order
* Focus-related state
* Configuration
* Associated surfaces

Conceptually:

```text
Window
   │
   ├── Position
   ├── Size
   ├── Visibility
   ├── Z-order
   └── Surface relationship
```

A window is primarily a **framework/window-management concept**.

It should not be treated as equivalent to a Surface.

### Window vs Surface

```text
Window
   ↓
"Where/how should this UI entity exist?"

Surface
   ↓
"Where does graphical content get produced?"
```

A window can be associated with one or more surfaces depending on the architecture and Android version.

---

# 7. SurfaceControl

**SurfaceControl** is an Android framework/native interface used to control properties of surfaces and their placement in the composition hierarchy.

It is primarily a **control object**, not a pixel container.

It can be used to control properties such as:

```text
Position
Size
Crop
Layer ordering
Visibility
Transform
Alpha
Parent/child relationship
Display association
```

Conceptually:

```text
SurfaceControl
       │
       ├── Position
       ├── Size
       ├── Crop
       ├── Transform
       ├── Alpha
       ├── Z-order
       └── Visibility
```

A useful mental model:

> **SurfaceControl controls how a surface/layer participates in composition.**

It does not itself contain the rendered pixels.

### Surface vs SurfaceControl

This distinction is critical:

```text
Surface
   ↓
Provides a way for a producer to work with buffers.

SurfaceControl
   ↓
Controls the surface/layer's composition properties.
```

Think:

```text
Surface
    = content production side

SurfaceControl
    = control/composition side
```

---

# 8. Layer

A **layer** is a composition-side representation of graphical content.

A layer can have:

```text
Buffer
Position
Size
Crop
Transform
Alpha
Z-order
Visibility
Composition properties
```

Conceptually:

```text
Layer
 ├── Buffer
 ├── Geometry
 ├── Crop
 ├── Transform
 ├── Alpha
 ├── Z-order
 └── Visibility
```

SurfaceFlinger works with layers when building the display composition.

For example:

```text
Layer A → Application
Layer B → Dialog
Layer C → Navigation bar
```

SurfaceFlinger can arrange them:

```text
        Layer C
        ─────────────
        Layer B
        ─────────────
        Layer A
        ─────────────
```

The exact internal representation and class hierarchy are implementation-dependent.

### Key point

> **Layer = composition-side representation of graphical content plus the state required to place and present it.**

---

# 9. GraphicBuffer

**GraphicBuffer** is an Android native graphics representation of a graphics buffer.

It provides the native-side representation needed to manage a buffer that can be shared between graphics components.

Conceptually:

```text
GraphicBuffer
     │
     ├── Width
     ├── Height
     ├── Format
     ├── Usage
     ├── Stride
     └── Native buffer resources
```

It is associated with the underlying graphics memory/resource and its metadata.

A simplified relationship:

```text
Application / Producer
          │
          ▼
       Buffer
          │
          ▼
     GraphicBuffer
          │
          ▼
SurfaceFlinger / Consumer
```

The actual Android implementation involves additional native buffer-management infrastructure.

### Important point

GraphicBuffer is **not a synonym for Surface**.

```text
Surface
   → interface for producing graphical content

GraphicBuffer
   → native representation/resource for the graphical buffer
```

---

# 10. AHardwareBuffer

**AHardwareBuffer** is an NDK API abstraction for hardware-accessible buffers.

It allows compatible buffer resources to be shared across different hardware/software components.

Typical users can include:

```text
CPU
GPU
Camera
Media
Display
Other hardware consumers
```

Conceptually:

```text
AHardwareBuffer
       │
       ├── Width
       ├── Height
       ├── Format
       ├── Usage
       └── Native hardware buffer resource
```

The important idea is interoperability.

For example:

```text
Camera
   │
   ▼
AHardwareBuffer
   │
   ├────────► GPU
   │
   └────────► Other compatible consumer
```

### GraphicBuffer vs AHardwareBuffer

They are related but serve different API roles.

```text
GraphicBuffer
   → Android native graphics infrastructure

AHardwareBuffer
   → Public NDK hardware-buffer API
```

They can represent or refer to the same underlying type of graphics buffer resource through Android's buffer infrastructure, but they are not simply two names for one API.

---

# 11. Framebuffer

The term **framebuffer** has multiple meanings depending on context.

At a conceptual level, a framebuffer is a buffer containing the image data used as a display/rendering target.

For example:

```text
Framebuffer
┌──────────────────────────┐
│ Display image            │
│                          │
│ 1920 × 1080              │
│                          │
└──────────────────────────┘
```

In Linux DRM/KMS terminology, a framebuffer object describes how a memory buffer is interpreted for display, including information such as:

* Pixel format
* Dimensions
* Memory layout
* Plane information

Therefore, avoid treating:

```text
framebuffer = any graphics buffer
```

as an exact definition.

In modern Android/Linux graphics, the actual architecture is more complex and uses buffer objects, DRM framebuffer objects, planes, and other resources.

### Key point

> **Framebuffer is a display-oriented representation of image data, but its exact meaning depends on the layer of the stack being discussed.**

---

# 12. Plane

A **plane** is a hardware display resource capable of scanning out a buffer as part of the display composition pipeline.

A display controller may have multiple planes.

For example:

```text
Plane 0 → Background
Plane 1 → Video
Plane 2 → UI
Plane 3 → Cursor
```

Conceptually:

```text
Buffer A ──► Plane 0
Buffer B ──► Plane 1
Buffer C ──► Plane 2
                 │
                 ▼
          Display Controller
                 │
                 ▼
               Panel
```

Hardware planes can support operations such as:

* Positioning
* Scaling
* Cropping
* Pixel-format handling
* Alpha/blending
* Transformation

The exact capabilities depend on the display hardware.

### Plane vs Layer

This distinction is extremely important:

```text
Layer
   → Android/software composition concept

Plane
   → Display-hardware composition resource
```

A layer may be assigned to a hardware plane when the hardware can support the required operations.

Not every Android layer maps to one hardware plane.

---

# 13. Display

A **display** is the destination to which graphical content is presented.

Examples:

```text
Internal phone display
External HDMI display
DisplayPort display
Virtual display
Automotive rear-seat display
```

A physical display can be represented by a pipeline such as:

```text
Layers
   │
   ▼
Composition
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

A display has properties such as:

```text
Resolution
Refresh rate
Color characteristics
Orientation
Timing
Connection state
```

### Important distinction

```text
Display
   ≠
Framebuffer
   ≠
Layer
   ≠
Surface
```

They exist at different conceptual levels.

---

# 14. Composition

**Composition** is the process of combining multiple graphical layers into the final display result.

Suppose:

```text
Layer A = Application
Layer B = Dialog
Layer C = System UI
```

Composition determines:

```text
Which layers are visible?
Where are they located?
What is their Z-order?
How are they blended?
What transforms are applied?
Which hardware/software path should be used?
```

Conceptually:

```text
Layer A
Layer B
Layer C
   │
   ▼
Composition
   │
   ▼
Final Display Image
```

Composition may be performed by:

```text
GPU
Display hardware
GPU + Display hardware
```

### Key point

> **Composition combines already-produced graphical content.**

It is different from rendering.

---

# 15. Rendering

**Rendering** is the process of generating graphical content.

For example:

```text
Text
Image
Button
Shape
Animation
```

may be rendered into a buffer.

Conceptually:

```text
UI Description
      │
      ▼
Rendering
      │
      ▼
Graphics Buffer
```

Typical Android rendering components include:

```text
RenderThread
Skia
GPU
```

depending on the rendering path.

### Rendering vs Composition

```text
Rendering:
"Create the content."

Composition:
"Combine the content."
```

Example:

```text
Render App UI
      │
      ▼
   Buffer A

Render Dialog
      │
      ▼
   Buffer B

Render System UI
      │
      ▼
   Buffer C

Buffer A + B + C
      │
      ▼
 Composition
      │
      ▼
 Display
```

---

# 16. Scanout

**Scanout** is the process in which display hardware reads image data and sends the corresponding pixel stream toward the display.

Conceptually:

```text
Display Memory
      │
      ▼
Display Controller
      │
      ▼
Pixel Stream
      │
      ▼
Panel
```

The display controller follows the configured timing and reads the required display data.

Scanout is therefore a **hardware/display operation**, not an application rendering operation.

### Important distinction

```text
Rendering
   → produces graphical content

Composition
   → combines graphical content

Scanout
   → reads display data and sends pixels to the display
```

---

# 17. Present

**Present** refers to making a completed frame available to the display pipeline for display at the appropriate time.

Conceptually:

```text
Composition Complete
        │
        ▼
      Present
        │
        ▼
Display Hardware
        │
        ▼
     Scanout
```

Presentation is closely connected with:

* Display timing
* VSync
* Buffer readiness
* Synchronization
* HWC
* Display controller

In Android, SurfaceFlinger coordinates with the hardware composer/display system to present frames.

### Important distinction

```text
Composition
   → produces/defines the display result

Present
   → submits that result for display

Scanout
   → hardware reads the display data
```

These stages are related but not identical.

---

# 18. Latch

**Latch** means that a consumer accepts a particular buffer or state as the one it will use for a subsequent stage of processing.

In Android graphics, the term is commonly used when discussing SurfaceFlinger acquiring the buffer/state that should participate in composition.

Conceptually:

```text
Producer
   │
   │ produces buffer
   ▼
Buffer available
   │
   ▼
SurfaceFlinger
   │
   │ Latch / acquire
   ▼
Buffer becomes the current
buffer for composition
```

A latch is therefore a **state/buffer transition**, not a rendering operation.

### Why latching matters

The producer and consumer operate asynchronously.

The producer may be rendering one buffer while SurfaceFlinger is using another.

For example:

```text
Buffer A → currently being displayed/consumed
Buffer B → being rendered
Buffer C → available
```

At an appropriate point, the consumer can acquire/latch a new buffer.

This prevents the display pipeline from using partially updated content.

The exact latch timing and terminology depend on the specific Android graphics path and synchronization model.

---

# 19. Putting the Terms Together

The easiest way to understand these terms is to connect them.

Suppose an application renders a UI.

### Step 1 — Application creates content

```text
Application
   │
   ▼
UI description
```

---

### Step 2 — Rendering generates pixels

```text
UI description
      │
      ▼
Rendering
      │
      ▼
Image content
```

---

### Step 3 — Pixels are stored in a buffer

```text
Image content
      │
      ▼
Graphics Buffer
```

The native Android graphics stack may represent/manage this resource using mechanisms such as:

```text
GraphicBuffer
AHardwareBuffer
```

depending on the API and path.

---

### Step 4 — Surface provides producer access

```text
Application
      │
      ▼
Surface
      │
      ▼
BufferQueue
      │
      ▼
Graphics Buffer
```

---

### Step 5 — SurfaceFlinger works with layers

The buffer becomes associated with composition state represented by a layer.

```text
Layer
 ├── Buffer
 ├── Position
 ├── Size
 ├── Crop
 ├── Transform
 ├── Alpha
 └── Z-order
```

---

### Step 6 — SurfaceControl changes composition state

For example:

```text
SurfaceControl
      │
      ├── Position
      ├── Size
      ├── Crop
      ├── Transform
      ├── Alpha
      └── Z-order
```

The buffer itself does not necessarily change when only these properties change.

---

### Step 7 — Multiple layers are composed

```text
Layer A
Layer B
Layer C
   │
   ▼
Composition
```

Composition may use:

```text
GPU
```

or:

```text
Hardware Planes
```

or both.

---

### Step 8 — Frame is presented

```text
Composition Result
       │
       ▼
     Present
       │
       ▼
Display Pipeline
```

---

### Step 9 — Display hardware performs scanout

```text
Display Buffer / Planes
          │
          ▼
Display Controller
          │
          ▼
       Scanout
          │
          ▼
        Panel
```

---

# 20. Relationship Between the Core Terms

The following diagram is the most useful reference model for this page:

```text
                    APPLICATION
                         │
                         │ produces content
                         ▼
                    RENDERING
                         │
                         │ generates pixels
                         ▼
                       IMAGE
                         │
                         │ stored in
                         ▼
                      BUFFER
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
        GraphicBuffer        AHardwareBuffer
              │                     │
              └──────────┬──────────┘
                         │
                         ▼
                      SURFACE
                         │
                         │ producer path
                         ▼
                    BUFFERQUEUE
                         │
                         │ consumer
                         ▼
                  SURFACEFLINGER
                         │
                         ▼
                       LAYER
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
      SurfaceControl           Layer State
              │                     │
              └──────────┬──────────┘
                         │
                         ▼
                    COMPOSITION
                         │
                 ┌───────┴───────┐
                 │               │
                 ▼               ▼
                GPU          HW PLANES
                 │               │
                 └───────┬───────┘
                         ▼
                      PRESENT
                         │
                         ▼
                 DISPLAY PIPELINE
                         │
                         ▼
                      SCANOUT
                         │
                         ▼
                      DISPLAY
```

---

# 21. The Most Important Distinctions

## Pixel vs Image

```text
Pixel
   → one picture element

Image
   → collection of pixels forming a picture
```

---

## Image vs Buffer

```text
Image
   → logical visual data

Buffer
   → memory/resource used to store that data
```

---

## Buffer vs Surface

```text
Buffer
   → contains graphical data

Surface
   → provides a producer-facing rendering target/interface
```

---

## Surface vs SurfaceControl

```text
Surface
   → produce graphical content

SurfaceControl
   → control composition properties
```

---

## Window vs Surface

```text
Window
   → framework/window-management concept

Surface
   → graphics producer/rendering concept
```

---

## Layer vs Surface

```text
Surface
   → producer-side concept

Layer
   → composition-side concept
```

---

## Layer vs Plane

```text
Layer
   → Android composition object/state

Plane
   → hardware display composition resource
```

---

## Rendering vs Composition

```text
Rendering
   → creates graphical content

Composition
   → combines graphical content
```

---

## Composition vs Scanout

```text
Composition
   → determines/creates the display image

Scanout
   → display hardware reads and transmits display pixels
```

---

## Present vs Scanout

```text
Present
   → makes a frame available for display

Scanout
   → hardware reads the display data
```

---

## Buffer vs Frame

```text
Buffer
   → storage/resource

Frame
   → unit of display update/presentation
```

One frame may involve multiple buffers.

One buffer can also participate in more than one stage of the graphics pipeline over its lifetime.

---

# 22. Reference Dictionary

| Term            | Simple meaning                               | Primary role                     |
| --------------- | -------------------------------------------- | -------------------------------- |
| Pixel           | One picture element                          | Image data                       |
| Image           | 2D visual pixel data                         | Visual representation            |
| Frame           | Content intended for one display update      | Presentation unit                |
| Buffer          | Memory/resource containing data              | Storage/transport                |
| Surface         | Producer-facing rendering target/interface   | Content production               |
| Window          | Framework window-management object           | Window management                |
| SurfaceControl  | Controls surface/layer properties            | Composition control              |
| Layer           | Composition-side graphical object/state      | Composition                      |
| GraphicBuffer   | Native graphics buffer representation        | Buffer management                |
| AHardwareBuffer | NDK hardware-buffer abstraction              | Buffer interoperability          |
| Framebuffer     | Display-oriented image-buffer representation | Display pipeline                 |
| Plane           | Hardware display composition resource        | Hardware composition             |
| Display         | Destination for presented content            | Presentation                     |
| Composition     | Combines graphical layers                    | Display composition              |
| Rendering       | Generates graphical content                  | Content generation               |
| Scanout         | Hardware reads display data for output       | Display hardware                 |
| Present         | Makes frame available for display            | Presentation                     |
| Latch           | Consumer accepts a buffer/state for use      | Synchronization/state transition |

---

# 23. One Complete Example

Consider a phone displaying:

```text
┌────────────────────────────────────┐
│ Status Bar                         │
│                                    │
│        Video                       │
│                                    │
│          ┌──────────────────┐      │
│          │      Dialog      │      │
│          └──────────────────┘      │
│                                    │
│ Navigation Bar                     │
└────────────────────────────────────┘
```

There may be multiple graphical sources:

```text
Status Bar   → Buffer A
Video        → Buffer B
Dialog       → Buffer C
Navigation   → Buffer D
```

Those buffers become associated with composition state:

```text
Layer A → Status Bar
Layer B → Video
Layer C → Dialog
Layer D → Navigation Bar
```

SurfaceFlinger evaluates the layers:

```text
Position
Size
Crop
Transform
Alpha
Z-order
Visibility
```

Then the composition strategy is determined.

Possible result:

```text
Layer A ──► Hardware Plane
Layer B ──► Hardware Plane
Layer C ──► GPU composition
Layer D ──► Hardware Plane
```

The resulting composition is then presented.

Finally:

```text
Present
   ↓
Display Controller
   ↓
Scanout
   ↓
Panel
   ↓
User sees the frame
```

This example demonstrates why a **layer is not necessarily a buffer**, and a **buffer is not necessarily a frame**.

---

# 24. Final Mental Model

Do not memorize the terms as isolated definitions.

Remember this chain:

```text
PIXEL
  ↓
IMAGE
  ↓
BUFFER
  ↓
SURFACE
  ↓
BUFFERQUEUE
  ↓
LAYER
  ↓
COMPOSITION
  ↓
PRESENT
  ↓
SCANOUT
  ↓
DISPLAY
```

With the important control objects alongside it:

```text
WINDOW
   │
   ▼
SURFACECONTROL
   │
   ▼
LAYER STATE
```

And the hardware representation:

```text
BUFFER
   │
   ├── GraphicBuffer
   └── AHardwareBuffer

LAYER
   │
   ▼
HARDWARE PLANE
   │
   ▼
DISPLAY CONTROLLER
```

## The Core Questions

When you encounter any graphics term, ask:

```text
1. Is this describing PIXELS?
2. Is this describing MEMORY?
3. Is this describing a PRODUCER?
4. Is this describing a WINDOW?
5. Is this describing COMPOSITION STATE?
6. Is this describing HARDWARE?
7. Is this describing TIMING/PRESENTATION?
```

That classification prevents most terminology confusion in Android graphics.

## One-Line Summary

```text
Pixel     → one picture element
Image     → collection of pixels
Buffer    → resource that stores graphical data
Surface   → producer-facing rendering target
Window    → framework window-management object
Layer     → composition-side graphical object/state
SurfaceControl → controls layer/surface properties
GraphicBuffer → native graphics-buffer representation
AHardwareBuffer → NDK hardware-buffer abstraction
Framebuffer → display-oriented buffer representation
Plane     → hardware composition resource
Rendering → creates graphical content
Composition → combines graphical content
Present   → makes a frame available for display
Latch     → consumer accepts a buffer/state for use
Scanout   → display hardware reads and outputs pixels
Display   → destination where pixels are shown
```

This dictionary should be treated as the vocabulary foundation for the subsequent geometry, buffer, rendering, SurfaceFlinger, HWC, and frame-lifecycle topics.
