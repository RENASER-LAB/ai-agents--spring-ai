# 罗小黑 look mechanics

## Natural motion

罗小黑 is a compact seated cartoon cat with an oversized rounded head and two very large cream oval eyes. The lower body, forepaws, and tail base stay planted. Gaze begins with the complete eye construction—the cream eye surfaces, black pupils, eyelids, rims, and highlights move or redraw together as physical eyes—then the head follows with a restrained pitch or yaw. The nearer ear and cheek become slightly more prominent during horizontal turns; the far side becomes slightly occluded. The torso follows only a little, and the curled tail remains attached and nearly stationary with very small continuous follow-through. There is no prop.

Do not slide only the black pupils across unchanged cream ovals, add a second eye layer, or rotate/tilt the whole sprite. Preserve the original broad-eyed, gentle expression and facial proportions.

## Anchors and motion budget

- Stable anchor: forepaws, seated lower torso, and tail base stay on one shared baseline and do not translate between frames.
- Eye lead: the paired cream eye surfaces and black pupils visibly aim first; eyelids reshape naturally, especially for up and down.
- Head follow: small pitch for up/down and small yaw for left/right, without skull stretching or body rotation.
- Ear/cheek follow: reveal slightly more of the side opposite the gaze and slightly occlude the far side, progressing continuously.
- Tail follow: the curled tail remains attached in the same practical location, with at most a subtle lag.
- Each 22.5-degree step uses roughly one quarter of the change between neighboring cardinal families. No adjacent step may jump in head size, eye spacing, baseline, body position, or tail attachment.

## Cardinal pose families

- `000 up`: broadly frontal. Both complete eyes aim toward the top edge; upper eyelids open/raise and the head pitches up slightly. The seated base and tail stay fixed.
- `090 screen-right`: head and face yaw toward the viewer's right. Both pupils and cream eye surfaces read on the screen-right side of the head center, the screen-left cheek/ear is a little more exposed, and the screen-right side is slightly foreshortened. Base and tail remain anchored.
- `180 down`: broadly frontal. Both complete eyes aim toward the bottom edge, lower lids compress slightly, and the head bows just enough to read downward while the body remains planted.
- `270 screen-left`: inverse of `090`. Head and face yaw toward the viewer's left; both pupils and cream eye surfaces read on the screen-left side of the head center, the screen-right cheek/ear is a little more exposed, and the screen-left side is slightly foreshortened. Base and tail remain anchored.

## Continuity

Row 9 advances evenly from up through the screen-right family to down. Row 10 continues from down through the screen-left family back to one step before up. Diagonals combine the neighboring cardinal eye, eyelid, and head cues without independently re-centering or restyling the cat. The loop should feel like one calm, curious glance around the clock.
