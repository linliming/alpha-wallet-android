package com.alphawallet.app.ui.widget;


import java.math.BigInteger;

public interface OnAssetSelectListener
{
    void onAssetSelected(BigInteger tokenId, NFTAsset asset, int position);
    void onAssetUnselected();
}
