package io.mosip.kernel.uingenerator.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response dto for uin generator
 *
 * @author Lalith Kota
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetBulkUinsResponseDto {

	/**
	 * The uin
	 */
	private List<String> uins;

}
