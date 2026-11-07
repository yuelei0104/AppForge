declare namespace API {
  type ChatHistory = {
    id?: string | number
    appId?: string | number
    userId?: string | number
    message?: string
    messageType?: string
    createTime?: string
    updateTime?: string
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    id?: string
    appId?: string
    userId?: string
    message?: string
    messageType?: string
    sortField?: string
    sortOrder?: string
  }

  type listAppChatHistoryParams = {
    appId: string
    pageSize?: number
    lastCreateTime?: string
    lastId?: string
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    pageNumber?: number
    pageSize?: number
    totalRow?: number
    totalPage?: number
  }

  type BaseResponsePageChatHistory = {
    code?: number
    message?: string
    data?: PageChatHistory
  }
}
