# Map base layers

The Projects Map uses the existing Leaflet instance and offers two mutually
exclusive base layers:

- **OpenStreetMap** is the default. Its existing attribution is retained.
- **Satellite** uses NASA Global Imagery Browse Services (GIBS), specifically
  `VIIRS_SNPP_CorrectedReflectance_TrueColor` through the public WMTS endpoint.
  NASA GIBS does not require an API key or introduce a paid dependency. The
  Leaflet attribution control displays `NASA GIBS / NASA EOSDIS` whenever this
  layer is active.

The NASA GIBS imagery layer is intentionally loaded only after a user selects
Satellite. It has a native maximum zoom of 9; Leaflet scales it above that
level so that project markers and normal map interaction remain available.

Changing a map layer never reinitializes Leaflet: the centre, zoom, filters,
markers, selected marker popup, and fullscreen state remain intact. Changing
the OMS language rebuilds only Leaflet's layer-control labels and keeps the
same tile-layer objects attached to the map.

NASA GIBS acknowledgement and service information:

- https://www.earthdata.nasa.gov/eosdis/science-system-description/eosdis-components/gibs
- https://earthdata.nasa.gov/data/tools/gibs
