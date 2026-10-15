package com.fpmislata.prestecs.di

import com.fpmislata.prestecs.data.prestecs.ApiPrestecsRepository
import com.fpmislata.prestecs.data.prestecs.PrestecsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun bindPrestecsRepository(impl: ApiPrestecsRepository): PrestecsRepository
}
