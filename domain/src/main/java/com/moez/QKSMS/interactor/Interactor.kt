/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.interactor

import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

abstract class Interactor<in Params> : Disposable {

    private val disposables: CompositeDisposable = CompositeDisposable()

    abstract fun buildObservable(params: Params): Flowable<*>

    fun execute(params: Params, onComplete: () -> Unit = {}) {
        com.moez.QKSMS.common.util.SendDebugLogger.log("${javaClass.simpleName}: execute called")
        disposables += buildObservable(params)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    com.moez.QKSMS.common.util.SendDebugLogger.log("${javaClass.simpleName}: onComplete")
                    onComplete()
                }
                .subscribe({
                    com.moez.QKSMS.common.util.SendDebugLogger.log("${javaClass.simpleName}: item processed")
                }, { error ->
                    com.moez.QKSMS.common.util.SendDebugLogger.log("${javaClass.simpleName} ERROR: ${error.javaClass.simpleName}: ${error.message}")
                    Timber.w(error)
                })
    }

    override fun dispose() {
        return disposables.dispose()
    }

    override fun isDisposed(): Boolean {
        return disposables.isDisposed
    }

}
