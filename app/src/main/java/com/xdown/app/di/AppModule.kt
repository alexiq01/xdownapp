package com.xdown.app.di

import com.xdown.app.data.remote.DownloadService
import com.xdown.app.data.remote.XScraper
import com.xdown.app.data.repository.MediaRepository
import com.xdown.app.domain.usecase.DownloadMediaUseCase
import com.xdown.app.domain.usecase.FetchMediaUseCase
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideXScraper(): XScraper {
        return XScraper()
    }

    @Provides
    @Singleton
    fun provideDownloadService(): DownloadService {
        return DownloadService()
    }

    @Provides
    @Singleton
    fun provideMediaRepository(
        scraper: XScraper,
        downloadService: DownloadService,
        @ApplicationContext context: Context
    ): MediaRepository {
        return MediaRepository(scraper, downloadService, context)
    }

    @Provides
    @Singleton
    fun provideFetchMediaUseCase(
        repository: MediaRepository
    ): FetchMediaUseCase {
        return FetchMediaUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideDownloadMediaUseCase(
        repository: MediaRepository
    ): DownloadMediaUseCase {
        return DownloadMediaUseCase(repository)
    }
}
