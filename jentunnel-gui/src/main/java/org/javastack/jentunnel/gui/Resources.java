package org.javastack.jentunnel.gui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.formdev.flatlaf.extras.FlatSVGUtils;

public class Resources {
	static AppInfo appInfo = AppInfo.getInstance();
	// https://www.drawsvg.org/drawsvg.html
	// https://svgsilh.com/
	// https://www.rapidtables.com/web/color/RGB_Color.html
	// https://www.materialui.co/flatuicolors
	// https://fontawesome.com/icons?d=gallery
	// https://github.com/FortAwesome/Font-Awesome/blob/master/svgs/
	static List<Image> mainIcons = FlatSVGUtils.createWindowIconImages("/images/ring.svg");
	static ImageIcon userIcon = loadIconSVG("/images/user.svg");
	static ImageIcon chainIcon = loadIconSVG("/images/chain.svg");
	static ImageIcon forwardIcon = loadIconSVG("/images/random.svg");
	// static ImageIcon loggingIcon = loadIconSVG("/images/logging.svg");

	static ImageIcon connectIcon = loadIconSVG("/images/connect.svg");
	static ImageIcon disconnectIcon = loadIconSVG("/images/disconnect.svg");

	static ImageIcon addIcon = loadIconSVG("/images/add.svg");
	static ImageIcon editIcon = loadIconSVG("/images/edit.svg");
	static ImageIcon copyIcon = loadIconSVG("/images/copy.svg");
	static ImageIcon deleteIcon = loadIconSVG("/images/trash.svg");
	static ImageIcon saveIcon = loadIconSVG("/images/save.svg");
	static ImageIcon keyIcon = loadIconSVG("/images/key.svg");
	static ImageIcon settingsIcon = loadIconSVG("/images/settings.svg");
	static ImageIcon themeIcon = loadIconSVG("/images/mask.svg");
	static ImageIcon exitIcon = loadIconSVG("/images/exit.svg");

	static ImageIcon editPassIcon = loadIconSVG("/images/edit-pass.svg");
	static ImageIcon fileUploadIcon = loadIconSVG("/images/file-upload.svg");

	static ImageIcon checkIcon = loadIconSVG("/images/circle-check.svg");
	static ImageIcon noCheckIcon = loadIconSVG("/images/circle.svg");

	static ImageIcon typeLocalIcon = loadIconSVG("/images/type-local.svg");
	static ImageIcon typeRemoteIcon = loadIconSVG("/images/type-remote.svg");
	static ImageIcon typeDynamicIcon = loadIconSVG("/images/type-dynamic.svg");

	static ImageIcon disconnectedIcon = loadIconSVG("/images/unlink.svg");
	static ImageIcon noConnectedIcon = loadIconSVG("/images/circle-dot.svg");
	static ImageIcon connectedIcon = loadIconSVG("/images/connect-ok.svg");
	static AnimatedIcon spinnerIcon = loadIconSVG( //
			"/images/spinner/circle-spinner-1.svg", //
			"/images/spinner/circle-spinner-1b.svg", //
			"/images/spinner/circle-spinner-2.svg", //
			"/images/spinner/circle-spinner-2b.svg", //
			"/images/spinner/circle-spinner-3.svg", //
			"/images/spinner/circle-spinner-3b.svg", //
			"/images/spinner/circle-spinner-4.svg", //
			"/images/spinner/circle-spinner-4b.svg" //
	);
	static AnimatedIcon spinnerIconBig = loadIconSVG(8f, //
			"/images/spinner/circle-spinner-1.svg", //
			"/images/spinner/circle-spinner-1b.svg", //
			"/images/spinner/circle-spinner-2.svg", //
			"/images/spinner/circle-spinner-2b.svg", //
			"/images/spinner/circle-spinner-3.svg", //
			"/images/spinner/circle-spinner-3b.svg", //
			"/images/spinner/circle-spinner-4.svg", //
			"/images/spinner/circle-spinner-4b.svg" //
	);

	static Image mainIcon = loadImageSVG("/images/ring.svg");
	static Image iconGrey = loadImagePNG("/images/icon_grey.png");
	static Image iconRed = loadImagePNG("/images/icon_red.png");
	static Image iconYellow = loadImagePNG("/images/icon_yellow.png");
	static Image iconGreen = loadImagePNG("/images/icon_green.png");

	private static final RuntimeException resourceNotFound(final String name) {
		return new RuntimeException("Resource not found: " + name) {
			private static final long serialVersionUID = 42L;

			@Override
			public synchronized Throwable fillInStackTrace() {
				return this;
			}
		};
	}

	private static ImageIcon loadIconSVG(final String location) {
		final BufferedImage image = FlatSVGUtils.svg2image(location, 1f); // 16 x 16
		if (image == null) {
			throw resourceNotFound(location);
		}
		return new ImageIcon(image);
	}

	private static AnimatedIcon loadIconSVG(final String... locations) {
		return loadIconSVG(1f, locations);
	}

	private static AnimatedIcon loadIconSVG(final float size, final String... locations) {
		final ImageIcon[] imgs = new ImageIcon[locations.length];
		for (int i = 0; i < locations.length; i++) {
			final BufferedImage image = FlatSVGUtils.svg2image(locations[i], size);
			if (image == null) {
				throw resourceNotFound(locations[i]);
			}
			imgs[i] = new ImageIcon(image); // 16 x 16
		}
		return new AnimatedIcon(imgs);
	}

	private static ImageIcon loadIconPNG(final String location) {
		final URL imageURL = Resources.class.getResource(location);
		if (imageURL == null) {
			throw resourceNotFound(location);
		}
		return new ImageIcon(imageURL);
	}

	private static Image loadImageSVG(final String location) {
		final BufferedImage image = FlatSVGUtils.svg2image(location, 1f); // 16 x 16
		if (image == null) {
			throw resourceNotFound(location);
		}
		return image;
	}

	private static Image loadImagePNG(final String location) {
		final URL imageURL = Resources.class.getResource(location);
		if (imageURL == null) {
			throw resourceNotFound(location);
		}
		// https://docs.oracle.com/javase/tutorial/uiswing/components/icon.html
		return (new ImageIcon(imageURL)).getImage();
	}

	static class AnimatedIcon implements Icon, Cloneable {
		private final ImageIcon[] icons;
		private volatile int frame = 0;

		public AnimatedIcon(final ImageIcon[] icons) {
			this.icons = icons;
		}

		public void nextFrame() {
			frame++;
		}

		private Icon getIcon() {
			return icons[frame % icons.length];
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			getIcon().paintIcon(c, g, x, y);
		}

		@Override
		public int getIconWidth() {
			return getIcon().getIconWidth();
		}

		@Override
		public int getIconHeight() {
			return getIcon().getIconWidth();
		}

		public AnimatedIcon copy() {
			return new AnimatedIcon(icons);
		}
	}
}
