package me.brook.selection.screens;

import com.badlogic.gdx.utils.ScreenUtils;

import me.brook.selection.LibDisplay;

public class FastScreen extends DisplayScreen {


	public FastScreen(LibDisplay display) {
		super(display);
	}

	@Override
	public void show() {
		super.show();
	}

	@Override
	public void render(float delta) {
		ScreenUtils.clear(0, 0, 0, 1);
		display.drawInfo(true);
	}

	@Override
	public void hide() {
		super.hide();
	}

}
