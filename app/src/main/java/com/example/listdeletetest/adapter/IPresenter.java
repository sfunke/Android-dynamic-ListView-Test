
package com.example.listdeletetest.adapter;

import android.content.Context;
import android.view.View;

public interface IPresenter<E extends View, T extends IAdapterItem> {
	public E getView(Context context);

	public void present(E itemView, T item);
}

