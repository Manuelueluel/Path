package it.traccia.core.ui.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface PermissionChecker {
    /** True when at least one of the manifest permissions is granted, or no runtime grant is needed. */
    fun isGranted(permission: AppPermission): Boolean
}

internal class AndroidPermissionChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PermissionChecker {
        override fun isGranted(permission: AppPermission): Boolean =
            !permission.isRuntimePermission() ||
                permission.manifestPermissions.any {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionModule {
    @Binds
    abstract fun bindPermissionChecker(impl: AndroidPermissionChecker): PermissionChecker
}
