package com.moderndoors.door;

/**
 * The three kinds of door. A door fills whatever opening its frame outlines, up to {@link #MAX_WIDTH} blocks across
 * and {@link #MAX_HEIGHT} tall. Speed is how much of the opening animation happens each tick (1.0 is the whole swing).
 */
public enum DoorKind {
	/** One wide panel that swings on a pivot in the middle of its hinge-side block, like a mansion's front door. */
	PIVOT("pivot", "Pivot Door", 1, 2, 0.05F),
	/** Glass panels, one per block across, that fold up against each other like an accordion at the hinge side. */
	FOLDING("folding", "Folding Glass Door", 2, 3, 0.06F),
	/** Two glass panels: the one on the hinge side stays put and the other glides behind it. */
	SLIDING("sliding", "Sliding Glass Door", 2, 2, 0.07F);

	public static final int MAX_WIDTH = 8;
	public static final int MAX_HEIGHT = 6;
	public static final int MIN_HEIGHT = 2;
	/** The size of doors placed before doors could be any size. */
	public static final int OLD_HEIGHT = 3;

	private final String id;
	private final String displayName;
	private final int minWidth;
	private final int oldWidth;
	private final float speed;

	DoorKind(String id, String displayName, int minWidth, int oldWidth, float speed) {
		this.id = id;
		this.displayName = displayName;
		this.minWidth = minWidth;
		this.oldWidth = oldWidth;
		this.speed = speed;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public int minWidth() {
		return minWidth;
	}

	public int oldWidth() {
		return oldWidth;
	}

	public float speed() {
		return speed;
	}
}
