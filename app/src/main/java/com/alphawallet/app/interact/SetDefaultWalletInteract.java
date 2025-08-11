package com.alphawallet.app.interact;

import com.alphawallet.app.repository.WalletRepositoryTypeJ;
import com.alphawallet.app.entity.Wallet;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class SetDefaultWalletInteract {

	private final WalletRepositoryTypeJ accountRepository;

	public SetDefaultWalletInteract(WalletRepositoryTypeJ walletRepositoryType) {
		this.accountRepository = walletRepositoryType;
	}

	public Completable set(Wallet wallet) {
		return accountRepository
				.setDefaultWallet(wallet)
				.observeOn(AndroidSchedulers.mainThread());
	}
}
