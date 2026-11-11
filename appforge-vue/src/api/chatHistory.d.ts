declare namespace API {
  type ChatHistory = {
    id?: string | number
    appId?: string | number
    userId?: string | number
    message?: string
    messageType?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
    attachments?: {
      id?: string | number
      kind?: string
      name?: string
      mimeType?: string
      size?: string | number
      url?: string
    }[]
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

declare namespace API {
  type ChatStatistics = {
    roundCount: number | string
    messageCount: number | string
    aiMessageCount: number | string
    lastMessageTime?: string
  }
  type ChatMemory = {
    enabled: boolean
    running: boolean
    triggerMessages: number
    recentMessages: number
    coveredMessages: number | string
    summary?: string
    updatedTime?: string
    lastError?: string
  }
}
