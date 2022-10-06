package com.example.stockmarketapp.data.repository

import com.example.stockmarketapp.data.csv.CSVParser
import com.example.stockmarketapp.data.local.StockDatabase
import com.example.stockmarketapp.data.mapper.toCompanyInfo
import com.example.stockmarketapp.data.mapper.toCompanyList
import com.example.stockmarketapp.data.mapper.toCompanyListingEntity
import com.example.stockmarketapp.data.remote.StockApi
import com.example.stockmarketapp.domain.model.CompanyInfo
import com.example.stockmarketapp.domain.model.CompanyListing
import com.example.stockmarketapp.domain.model.IntraDayInfo
import com.example.stockmarketapp.domain.repository.StockRepository
import com.example.stockmarketapp.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl
@Inject constructor(
    private val api:StockApi,
    private val db:StockDatabase,
    private val companyListingParser: CSVParser<CompanyListing>,
    private val intraDayInfoParser: CSVParser<IntraDayInfo>
):StockRepository{

    private val dao=db.dao

    override suspend fun getCompanyListing(
        fetchFromRemote: Boolean,
        query: String
    ): Flow<Resource<List<CompanyListing>>> =
        flow {
            emit(Resource.Loading(true))
            val localListing=dao.searchCompanyListing(query)

            emit(Resource.Success(
                data=localListing.map {
                    it.toCompanyList()
                }
            ))

            val  isDbEmpty=localListing.isEmpty() && query.isEmpty()
            val loadFromCache=!isDbEmpty && !fetchFromRemote

            if(loadFromCache){
                emit(Resource.Loading(false))
                return@flow
            }

            val remoteListing = try {
                val response=api.getListings()
                companyListingParser.parse(response.byteStream())
            }
            catch (e: IOException) {
                e.printStackTrace()
                emit(Resource.Error("Could not load data"))
                null
            }
            catch (e: HttpException) {
                e.printStackTrace()
                emit(Resource.Error("Could not load data"))
                null
            }
            remoteListing?.let{listing->
                dao.clearCompanyListings()
                dao.insertCompanyListings(listing.map {
                    it.toCompanyListingEntity()
                })
                emit(Resource.Success(
                    data=dao.searchCompanyListing("").map {
                        it.toCompanyList()
                    }
                ))
                emit(Resource.Loading(false))
            }
        }

    override suspend fun getIntraDayInfo(symbol: String): Resource<List<IntraDayInfo>> {
        return try {
            val response=api.getIntraDayInfo(symbol)
            val result=intraDayInfoParser.parse(response.byteStream())
            Resource.Success(result)
        }
        catch (e:IOException){
            e.printStackTrace()
            Resource.Error("Couldn't load intraday info")
        }
        catch (e:HttpException){
            e.printStackTrace()
            Resource.Error("Couldn't load intraday info")
        }
    }

    override suspend fun getCompanyInfo(symbol: String): Resource<CompanyInfo> {
        return try {
            val result=api.getCompanyInfo(symbol)
            Resource.Success(result.toCompanyInfo())
        }
        catch (e:IOException){
            e.printStackTrace()
            Resource.Error("Couldn't load company info")
        }
        catch (e:HttpException){
            e.printStackTrace()
            Resource.Error("Couldn't load company info")
        }
    }


}