# Module D — AR demonstrations (Phase 5, not started)

Planned: a Unity 6 **LTS** project (not the 6000.5 beta pinned by arfoundation-samples) using
AR Foundation 6 + the ARCore XR Plug-in, exported as an Android library and launched from the
PhysioAR app as a separate activity ("Unity as a Library").

Scope:
- Plane detection and placement of an anchored 3D reference avatar on the floor
  (from arfoundation-samples: SimpleAR, PlaneDetection, Anchors). Licence: Unity Companion License.
- The avatar plays the prescribed movement; trajectories (e.g. the ankle arc in knee extension)
  are drawn as lines.
- Environment-depth occlusion where the device supports it.

Out of scope: body tracking in Unity. AR Foundation offers no human body tracking on ARCore
(*verify on target devices*), and measurement stays in the MediaPipe path (ADR-013).

Reference motions should be data (joint-angle curves per exercise, derived from the same
parameters as the prescription), so the demonstration matches what the engine will check.
