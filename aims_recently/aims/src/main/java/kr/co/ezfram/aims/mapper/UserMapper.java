package kr.co.ezfram.aims.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import kr.co.ezfram.aims.vo.UserVo;

@Mapper
@Repository
public interface UserMapper {
	
	public void insertUser(UserVo userVo);
	
	public List<UserVo> selectUserList(UserVo userVo);
	
	public UserVo selectUserById(String userId);
	
	public UserVo selectUserByIdIncludePassword(String userId);
	
	public Integer updateUser(UserVo userVo);
	
	public Integer deleteMember(Long id);
	
}
