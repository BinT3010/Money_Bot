package com.moneyset.app.data.source.remote

import com.moneyset.app.data.source.remote.dto.BillDto
import com.moneyset.app.data.source.remote.dto.BudgetDto
import com.moneyset.app.data.source.remote.dto.CategoryLimitDto
import com.moneyset.app.data.source.remote.dto.ExpenseDraftDto
import com.moneyset.app.data.source.remote.dto.LimitDto
import com.moneyset.app.data.source.remote.dto.ParseExpenseRequest
import com.moneyset.app.data.source.remote.dto.PayBillRequest
import com.moneyset.app.data.source.remote.dto.TotalBudgetDto
import com.moneyset.app.data.source.remote.dto.TransactionDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Hợp đồng API — xem README.md. month dạng "yyyy-MM", date dạng "yyyy-MM-dd". */
interface FinanceApi {

    @GET("transactions")
    suspend fun getTransactions(@Query("month") month: String): List<TransactionDto>

    @POST("transactions")
    suspend fun createTransaction(@Body body: TransactionDto): TransactionDto

    @GET("budgets/{month}")
    suspend fun getBudget(@Path("month") month: String): BudgetDto

    @PUT("budgets/{month}")
    suspend fun updateTotalBudget(@Path("month") month: String, @Body body: TotalBudgetDto): BudgetDto

    @PUT("budgets/{month}/categories/{category}")
    suspend fun updateCategoryLimit(
        @Path("month") month: String,
        @Path("category") category: String,
        @Body body: LimitDto,
    ): BudgetDto

    @GET("budgets/{month}/suggestions")
    suspend fun suggestLimits(@Path("month") month: String): List<CategoryLimitDto>

    @GET("bills")
    suspend fun getBills(): List<BillDto>

    @POST("bills")
    suspend fun createBill(@Body body: BillDto): BillDto

    @POST("bills/{id}/pay")
    suspend fun markBillPaid(@Path("id") id: String, @Body body: PayBillRequest): BillDto

    @POST("ai/parse-expense")
    suspend fun parseExpense(@Body body: ParseExpenseRequest): ExpenseDraftDto

    @Multipart
    @POST("ai/scan-receipt")
    suspend fun scanReceipt(
        @Part image: MultipartBody.Part,
        @Part("today") today: RequestBody,
    ): ExpenseDraftDto
}
