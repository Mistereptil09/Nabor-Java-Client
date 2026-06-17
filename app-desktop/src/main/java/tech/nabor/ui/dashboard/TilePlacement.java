package tech.nabor.ui.dashboard;

/** Position and span of a tile in the dashboard grid. */
public record TilePlacement(String tileId, int col, int row, int colSpan) {}
